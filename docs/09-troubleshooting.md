# Troubleshooting

## Docker service không chạy

Check:

```powershell
docker compose --env-file .env ps
```

Logs:

```powershell
docker compose --env-file .env logs --tail=200
```

---

## autojob-app không UP

```powershell
docker compose --env-file .env `
  logs --tail=200 autojob-app
```

Common causes:

```text
Mongo unavailable
CV parser unhealthy
embedding service loading model
invalid taxonomy
invalid matching config
version mismatch
```

---

## CV parser version mismatch

Expected current:

```text
rule-v2
```

`.env`:

```dotenv
CV_PARSER_VERSION=rule-v2
CV_PARSER_EXPECTED_VERSION=rule-v2
```

Rebuild:

```powershell
docker compose --env-file .env `
  up -d --build cv-parser-service autojob-app
```

Important:

`docker-compose.yml` vẫn có fallback `rule-v1` ở một số chỗ.

Luôn dùng:

```text
--env-file .env
```

---

## CV parser không ready

```powershell
Invoke-RestMethod `
  http://localhost:8003/ready
```

Logs:

```powershell
docker compose --env-file .env `
  logs --tail=200 cv-parser-service
```

---

## antiword error

DOC extraction dùng `antiword`.

Nếu DOC parse fail:

```text
antiword missing
binary execution failure
invalid old DOC file
timeout
```

Rebuild parser image thay vì chạy parser local host.

---

## CV parse status FAILED

Inspect:

```javascript
db.raw_cvs.findOne({ _id: "..." })
```

Check:

```text
status
lastError
bucket
objectKey
```

Then parser/app logs.

---

## CV ra DIRECTOR sai

Check trước:

```text
workExperiences[].jobTitle
workExperiences[].normalizedJobTitle
seniority
experienceYears
```

`Trợ lý giám đốc` phải:

```text
normalizedJobTitle = EXECUTIVE_ASSISTANT
```

và không tự tạo:

```text
DIRECTOR
```

Nếu vẫn sai, đảm bảo container đã rebuild từ seniority parser/taxonomy mới.

---

## experienceYears=2 nhưng ENTRY_LEVEL

Current expected:

```text
2.0 => MID
```

Nếu career objective `"sinh viên"` làm result thành ENTRY_LEVEL thì container đang chạy parser logic cũ.

Rebuild:

```powershell
docker compose --env-file .env `
  up -d --build cv-parser-service
```

---

## PowerShell làm hỏng tiếng Việt

Windows PowerShell 5.1 có thể làm Unicode pipe thành:

```text
Tr? l? gi?m ??c
```

Không dùng Bash heredoc:

```text
python - <<'PY'
```

trong PowerShell.

Với script test nên force UTF-8 hoặc dùng Unicode escape cho isolated Python smoke tests.

Real CV file qua MinIO/API không phụ thuộc terminal literal theo cách đó.

---

## Candidate embedding không có

Check:

```text
candidate_profiles đã persist?
raw_cvs.status = PARSED?
CandidateProfileReadyEvent listener có chạy?
embedding-service READY?
```

API:

```text
GET /api/admin/candidate-embeddings/{candidateProfileId}
```

Force:

```text
POST /api/admin/candidate-embeddings/{candidateProfileId}/rebuild?force=true
```

---

## Candidate embedding FAILED

Logs:

```powershell
docker compose --env-file .env `
  logs --tail=200 autojob-app
```

```powershell
docker compose --env-file .env `
  logs --tail=200 embedding-service
```

Check:

```text
embeddingVersion
dimension
textHash
lastError
```

---

## Matching says embedding not ready

Error:

```text
MATCHING_CANDIDATE_EMBEDDING_NOT_READY
```

Matching không tự build embedding.

Build/rebuild candidate embedding trước.

---

## Matching says embedding stale

Error:

```text
MATCHING_CANDIDATE_EMBEDDING_STALE
```

Candidate profile đã thay đổi sau embedding.

Rebuild:

```text
POST /api/admin/candidate-embeddings/{candidateProfileId}/rebuild?force=true
```

sau đó matching lại.

---

## Matching returns no results

Check lần lượt:

```text
Qdrant has compatible vectors
normalizationVersion = rule-v4
job textVersion = job-text-v2
embeddingVersion matches candidate
jobs not expired
jobs not > max age
acceptance thresholds
```

Current acceptance:

```text
final >= 0.45
semantic >= 0.50
```

sau đó còn skill/structured/strong-semantic gates.

---

## Matching returns fewer than 20

Đây có thể là behavior đúng.

Acceptance filter chạy trước:

```text
result-limit = 20
```

Nếu chỉ 8 job đủ relevance:

```text
matchedCount = 8
```

Service không fill bằng job yếu.

---

## Matching returns stale ranking

Check:

```text
reusedExisting
rankingVersion
candidateEmbeddingId
```

Nếu muốn rerun:

```text
?force=true
```

---

## Qdrant unavailable

Check:

```powershell
Invoke-RestMethod `
  http://localhost:6333/collections
```

Matching có thể trả:

```text
503 MATCHING_VECTOR_STORE_UNAVAILABLE
```

---

## Live crawler failed

External sites có thể:

```text
change HTML
return 403
return 429
redirect
require login
block automated request
```

Không bypass anti-bot.

Compare live HTML với fixture/parser assumptions trước khi sửa parser.

---

## Mongo script SyntaxError với rawCvId

UUID/string phải nằm trong quotes.

Correct mongosh:

```javascript
db.candidate_profiles.findOne({
  rawCvId: "uuid-here"
})
```

Không phải:

```javascript
rawCvId: uuid-here
```

---

## View latest candidate

```javascript
db.candidate_profiles
  .find()
  .sort({ updatedAt: -1 })
  .limit(1)
```

Latest embedding:

```javascript
db.candidate_embeddings
  .find()
  .sort({ updatedAt: -1 })
  .limit(1)
```

Latest matching:

```javascript
db.match_results
  .find()
  .sort({ generatedAt: -1 })
  .limit(20)
```