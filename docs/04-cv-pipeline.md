# CV Pipeline

## 1. Current flow

CV pipeline hiện đã hoạt động end-to-end:

```text
Upload
→ MinIO
→ raw_cvs
→ parse
→ candidate_profiles
→ candidate embedding
→ candidate_embeddings
→ matching
```

---

## 2. Upload

API:

```text
POST /api/cvs
```

Multipart field:

```text
file
```

Accepted:

```text
PDF
DOC
DOCX
```

Backend validate file structure/signature, không chỉ extension.

Default max:

```text
10 MB
```

---

## 3. Object storage

Bucket:

```text
autojob-cvs
```

Pattern:

```text
raw/yyyy/MM/dd/{rawCvId}/{safeFilename}
```

Raw metadata được persist vào:

```text
raw_cvs
```

Status:

```text
UPLOADED
PARSING
PARSED
FAILED
```

---

## 4. Parse API

```text
POST /api/cvs/{rawCvId}/parse
```

Java:

```text
CvParsingService
→ CvParserClient
→ HttpCvParserClient
→ cv-parser-service
```

Python nhận metadata object:

```text
rawCvId
bucket
objectKey
filename
contentType
```

Python tự đọc object từ MinIO.

---

## 5. Parser

Service:

```text
ai-services/cv-parser-service
```

Version:

```text
rule-v2
```

Responsibilities:

```text
text extraction
layout warnings
section detection
identity/contact
skills
work experience
education
certification
language
seniority
experience years
parse quality
```

---

## 6. Candidate profile persistence

Python chỉ trả response.

Java validate response rồi map sang:

```text
CandidateProfile
```

Persist:

```text
candidate_profiles
```

Candidate profile phải được persist trước khi event downstream được publish.

Sau đó:

```text
raw_cvs.status = PARSED
```

---

## 7. Candidate embedding trigger

Sau profile persistence:

```text
CandidateProfileReadyEvent
```

được publish.

Listener:

```text
CandidateProfileReadyEventListener
```

gọi:

```text
CandidateEmbeddingService
```

Embedding failure không rollback candidate profile.

Nếu embedding fail:

```text
raw_cvs        = PARSED
candidate      = tồn tại
embedding      = FAILED
```

---

## 8. Candidate seniority

Source:

```text
ai-services/cv-parser-service/app/parsing/seniority_parser.py
```

Taxonomy:

```text
configs/taxonomy/shared/seniority.yml
```

Resolution priority:

```text
1. explicit headline
2. explicit current/latest work role
3. experienceYears
4. career objective
5. target job title
```

---

## 9. Historical role policy

Không scan lịch sử rồi lấy bất kỳ leadership keyword nào làm current level.

Ví dụ:

```text
old role = Director
latest role = Consultant
```

không mặc định:

```text
DIRECTOR
```

Tương tự:

```text
old role = Intern
latest role = Engineer
```

không pin candidate thành:

```text
INTERN
```

---

## 10. Experience fallback

Current taxonomy:

```yaml
experience:
  entry-level-under: 0.5
  junior-under: 2.0
  mid-under: 5.0
```

Meaning:

```text
< 0.5           ENTRY_LEVEL
0.5 - < 2.0     JUNIOR
2.0 - < 5.0     MID
>= 5.0          SENIOR
```

Boundary:

```text
2.0 = MID
```

Nếu business muốn `2 years = JUNIOR`, chỉnh taxonomy threshold, không special-case parser.

---

## 11. Career objective

Career objective là weak evidence.

Ví dụ:

```text
"Là sinh viên..."
```

không được override:

```text
experienceYears = 2.0
```

Correct:

```text
experienceYears = 2.0
→ MID
```

không phải:

```text
ENTRY_LEVEL
```

---

## 12. Assistant vs leadership

Substring không đủ để infer seniority.

Không phải Director:

```text
Trợ lý giám đốc
Trợ lý tổng giám đốc
Thư ký ban giám đốc
Assistant to the Director
Director's Assistant
Executive Assistant to CEO
```

Director:

```text
Assistant Director
Deputy Director
Phó giám đốc
Giám đốc kinh doanh
```

---

## 13. Canonical title != seniority

Ví dụ:

```text
Trợ lý giám đốc
```

normalize:

```text
EXECUTIVE_ASSISTANT
```

Nhưng seniority không phải:

```text
EXECUTIVE
DIRECTOR
```

---

## 14. Unknown job titles

Job title không bắt buộc phải có canonical entry.

Ví dụ:

```text
DATACENTERS |
THỰC TẬP SINH KINH DOANH
```

Expected:

```text
companyName    = DATACENTERS
jobTitle       = THỰC TẬP SINH KINH DOANH
employmentType = INTERNSHIP
```

Không được vứt role chỉ vì:

```text
normalizedJobTitle = null
```

---

## 15. Candidate embedding

Candidate profile được chuyển thành deterministic embedding text.

Current version:

```text
candidate-text-v1
```

Embedding model:

```text
intfloat/multilingual-e5-small
```

Dimension:

```text
384
```

Persist:

```text
candidate_embeddings
```

Statuses:

```text
PROCESSING
READY
FAILED
```

---

## 16. Embedding idempotency

Candidate embedding identity phụ thuộc:

```text
candidateProfileId
embeddingVersion
textVersion
textHash
```

Nếu READY và text không đổi:

```text
force=false
→ reuse
```

Force rebuild:

```text
POST /api/admin/candidate-embeddings/{candidateProfileId}/rebuild?force=true
```

---

## 17. Matching handoff

Matching yêu cầu:

```text
CandidateProfile
+
READY CandidateEmbedding
```

Candidate embedding phải còn tương thích với current profile/parser version.

Matching sau đó dùng vector candidate search job vector trong Qdrant.

---

## 18. Verified real CV behavior

Một CV tiếng Việt thực đã verify:

```text
THỰC TẬP SINH KINH DOANH
→ DATACENTERS
→ INTERNSHIP
```

```text
Trợ lý giám đốc
→ EXECUTIVE_ASSISTANT
```

```text
experienceYears = 2.0
seniority       = MID
```

Không còn false positive:

```text
Trợ lý giám đốc
→ DIRECTOR
```

---

## 19. Verification

Use:

```text
scripts/test-cv-parse-embedding.ps1
```

Run:

```powershell
powershell -ExecutionPolicy Bypass `
  -File .\scripts\test-cv-parse-embedding.ps1
```

Fields cần inspect:

```text
careerObjective
workExperiences
experienceYears
seniority
recentJobTitles
parserWarnings
parserVersion
```