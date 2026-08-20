# Matching Engine

## 1. Status

Matching Engine hiện đã được triển khai và chạy trong:

```text
autojob-app
```

Module:

```text
backend/modules/matching
```

Module đã nằm trong:

```text
backend/pom.xml
```

và là dependency của:

```text
backend/autojob-app/pom.xml
```

---

## 2. API

Run:

```text
POST /api/matching/candidates/{candidateProfileId}
```

Force:

```text
POST /api/matching/candidates/{candidateProfileId}?force=true
```

Read current:

```text
GET /api/matching/candidates/{candidateProfileId}
```

---

## 3. Preconditions

Matching cần:

```text
candidate profile tồn tại
candidate thuộc owner hiện tại
READY candidate embedding tồn tại
candidate textVersion tương thích
embedding không stale
embedding vector hợp lệ
```

Nếu chưa ready, API trả precondition error thay vì tự chạy parse/embed ngầm.

---

## 4. Flow

```text
CandidateProfile
        +
CandidateEmbedding READY
        |
        v
Qdrant retrieval
        |
        v
Mongo hydrate normalized_jobs
        |
        v
Eligibility Filter
        |
        v
Semantic Calibration
        |
        v
Hybrid Scoring
        |
        v
Acceptance Filter
        |
        v
Sort + Limit
        |
        v
match_results
```

---

## 5. Retrieval configuration

Source:

```text
configs/matching/ranking.yml
```

Current:

```yaml
version: hybrid-v6-balanced-r4

retrieval:
  candidate-pool-size: 100
  result-limit: 20
```

Qdrant search filter:

```text
normalizationVersion = rule-v4
embeddingVersion     = candidate embedding version
textVersion          = job-text-v2
```

---

## 6. Job eligibility

Hard reject trước semantic calibration khi:

```text
job missing
job id missing
normalization version mismatch
deadline passed
postedAt quá cũ
```

Current max age:

```text
30 days
```

Hard filter chạy trước ranking để job invalid không làm méo calibration distribution.

---

## 7. Semantic calibration

Qdrant cosine score không được hiểu trực tiếp là phần trăm match.

Current calibration:

```text
p10 → relative lower
p90 → relative upper
minimum spread = 0.04

raw floor   = 0.75
raw ceiling = 0.95

relative weight = 0.65
```

Final semantic score kết hợp:

```text
relative pool score
+
absolute raw cosine score
```

---

## 8. Hybrid score

Current weights:

```text
semantic   0.40
skill      0.40
seniority  0.10
location   0.05
freshness  0.05
```

Formula về nguyên tắc:

```text
weighted known components
/
active weights
```

Nếu component không có evidence thì weight đó không được ép thành `0.5` và làm sai final score.

---

## 9. Skill score

Skill scorer dùng shared skill taxonomy.

Có phân biệt:

```text
core skill
generic skill
```

Generic skills:

```text
communication
presentation
problem-solving
critical-thinking
teamwork
time-management
```

Generic-only matching bị cap để soft skills không làm job unrelated leo ranking.

Evidence confidence:

```text
skills section       1.00
work experience      1.00
project              0.65
profile text         0.55
scoped text          0.50
unknown evidence     0.50
```

Output:

```text
skillScore
matchedSkills
missingSkills
```

---

## 10. Seniority score

Seniority scorer dùng:

```text
candidate seniority
job seniority
candidate experienceYears
job experienceMin
job experienceMax
```

Nếu có cả level và years:

```text
level score      75%
experience score 25%
```

Nếu không đủ data:

```text
0.5 = UNKNOWN/no-decision
```

Candidate parser seniority là signal chính.

Matching có fallback khi parser trả UNKNOWN.

---

## 11. Location score

Ưu tiên:

```text
preferredLocations
```

Nếu candidate không khai preference, scorer có thể fallback về current/contact location.

Current/home city không được coi như hard preference.

Vì vậy candidate sống ở HCM không đồng nghĩa job Hà Nội phải bị reject.

---

## 12. Freshness

Fresh:

```text
<= 7 days
→ 1.0
```

Old:

```text
>= 30 days
→ 0.0
```

Giữa 7 và 30 ngày decay tuyến tính.

---

## 13. Acceptance filter

Current config:

```text
minimum final        0.45
minimum semantic     0.50
minimum skill        0.10
strong skill         0.30
strong semantic      0.80
min structured       0.10
```

Logic:

```text
baseline final + semantic
        |
        v
strong skill?
        |
        + yes -> accept
        |
        no
        v
structured contradiction?
        |
        + yes -> reject
        |
        no
        v
moderate skill?
        |
        + yes -> accept
        |
        no
        v
strong semantic?
        |
        + yes -> accept
        + no  -> reject
```

Filter chạy trước `result-limit`.

Nếu chỉ 7 job đủ tốt:

```text
return 7
```

không lấy job yếu để lấp đủ 20.

---

## 14. Sorting

Primary:

```text
finalScore DESC
```

Tie breakers:

```text
semanticScore DESC
freshnessScore DESC
job id
Qdrant point id
```

---

## 15. Persistence

Collection:

```text
match_results
```

Mỗi document snapshot:

```text
candidate identity
job identity
job display fields
version fields
rank
score breakdown
matched skills
missing skills
generatedAt
```

Unique run/job key:

```text
candidateProfileId
+
candidateEmbeddingId
+
rankingVersion
+
normalizedJobId
```

---

## 16. Idempotency

Nếu:

```text
candidateEmbeddingId
+
rankingVersion
```

đã có result và:

```text
force=false
```

service reuse existing result.

Response:

```text
reusedExisting = true
```

Force rerun:

```text
force=true
```

sẽ replace exact run snapshot.

---

## 17. Presentation tier

API tính thêm:

```text
STRONG
STRETCH
POSSIBLE
EXPLORE
```

Tier chỉ phục vụ frontend explanation.

Nó không:

```text
thay finalScore
thay rank
reject job
thay Qdrant retrieval
```

---

## 18. Test status

Automated test trong module matching hiện còn mỏng.

Có:

```text
MatchingPropertiesTest
```

Real-data validation hiện được thực hiện bằng:

```text
CV thật
+
candidate profile thật
+
Mongo normalized jobs
+
Qdrant job vectors
+
matching result thật
```

Hai loại test phải được phân biệt:

```text
automated regression tests
vs
real-data ranking validation
```

---

## 19. Next testing work

Nên bổ sung automated test cho:

```text
SemanticScoreNormalizer
SkillScorer
SeniorityScorer
LocationScorer
FreshnessScorer
JobEligibilityFilter
MatchAcceptanceFilter
HybridRankingService
HybridMatchingService
```

Đặc biệt cần golden regression set từ CV/job thật.