# Testing and Verification

## 1. Test layers

AutoJob hiện được verify ở nhiều layer:

```text
unit/component tests
integration tests
Docker smoke tests
real-CV tests
real Mongo/Qdrant matching validation
```

Không được đồng nhất:

```text
ít unit test
```

với:

```text
feature chưa được test
```

Matching hiện có real-data validation dù automated scorer coverage còn mỏng.

---

# 2. Docker startup

```powershell
docker compose --env-file .env up -d --build
```

Check:

```powershell
docker compose --env-file .env ps
```

---

# 3. Java tests

Có thể chạy Maven tests trong backend dev environment khi cần regression.

Modules có automated tests gồm:

```text
job-crawler
job-normalizer
job-embedding
cv
candidate-embedding
matching
```

`matching` hiện chủ yếu có:

```text
MatchingPropertiesTest
```

nên scorer/service test coverage cần bổ sung.

---

# 4. CV parser tests

Python parser có unit/integration tests cho:

```text
PDF/DOC/DOCX extraction
section detection
identity/contact
skills
work experience
education
certification
language
seniority
FastAPI validation
MinIO integration
```

Seniority regression cần cover:

```text
Trợ lý giám đốc       -> not DIRECTOR
Assistant to Director -> not DIRECTOR
Assistant Director    -> DIRECTOR
Deputy Director       -> DIRECTOR
Phó giám đốc          -> DIRECTOR

Assistant to Manager  -> not MANAGER
Assistant Manager     -> MANAGER

Middle School Teacher -> not MID
Lead Generation       -> not LEAD
Team Lead              -> LEAD
```

---

# 5. Real CV test

Script:

```text
scripts/test-cv-parse-embedding.ps1
```

Example:

```powershell
$CvPath = "D:\test-data\cv2.pdf"
```

Run:

```powershell
powershell -ExecutionPolicy Bypass `
  -File .\scripts\test-cv-parse-embedding.ps1
```

Verify:

```text
upload response
rawCvId

parsed full JSON

seniority
experienceYears
recent job titles
work experiences
skills
warnings

candidate_profiles Mongo document
candidate_embeddings Mongo document

embedding status
dimension
vector length
normalization
```

---

# 6. Latest seniority regression expectation

For tested real CV:

```text
experienceYears = 2.0
seniority       = MID
```

Expected work experiences include:

```text
DATACENTERS
THỰC TẬP SINH KINH DOANH
INTERNSHIP
```

and:

```text
Trợ lý giám đốc
EXECUTIVE_ASSISTANT
```

Must not produce:

```text
DIRECTOR
```

Career objective containing:

```text
sinh viên
```

must not override structured experience.

---

# 7. Job pipeline smoke test

Mock:

```powershell
Invoke-RestMethod `
  -Method POST `
  http://localhost:8080/api/admin/crawlers/mock/run
```

Then:

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/raw-jobs?limit=20"
```

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/normalized-jobs?page=0&size=20"
```

Check job embedding:

```text
GET /api/job-embeddings/{normalizedJobId}
```

---

# 8. Live crawler test

Example:

```powershell
Invoke-RestMethod `
  -Method POST `
  "http://localhost:8080/api/admin/crawlers/live/ITVIEC/run?limit=5"
```

Do not interpret external website failure automatically as parser bug.

Possible causes:

```text
site HTML changed
403/429
network failure
anti-bot
redirect/login wall
```

---

# 9. Candidate embedding verification

Expected:

```text
status = READY
dimension = 384
normalized = true
vector.Count = dimension
textVersion = candidate-text-v1
```

---

# 10. Matching real-data test

Prerequisites:

```text
candidate profile exists
candidate embedding READY
normalized jobs exist
job embeddings READY
Qdrant contains compatible points
```

Run:

```powershell
Invoke-RestMethod `
  -Method POST `
  "http://localhost:8080/api/matching/candidates/$candidateProfileId?force=true"
```

Inspect:

```text
retrievedCount
loadedJobCount
matchedCount
rankingVersion

rank
finalScore
semanticScore
skillScore
seniorityScore
locationScore
freshnessScore

matchTier
matchedSkills
missingSkills
explanations
```

---

# 11. Matching acceptance quality

Do not validate only:

```text
rank #1
```

Review:

```text
top 5/top 10 job relevance
obvious false positives
skill overlap
seniority gaps
location behavior
semantic-only matches
expired/old jobs
```

---

# 12. Automated matching tests still needed

Priority:

```text
SemanticScoreNormalizerTest
SkillScorerTest
SeniorityScorerTest
LocationScorerTest
FreshnessScorerTest
JobEligibilityFilterTest
MatchAcceptanceFilterTest
HybridRankingServiceTest
HybridMatchingServiceTest
```

Use anonymized real-data cases as golden fixtures where possible.

---

# 13. Mongo verification

```powershell
docker exec -it autojob-mongo `
  mongosh `
  -u root `
  -p password `
  --authenticationDatabase admin `
  autojob
```

Examples:

```javascript
db.candidate_profiles
  .find()
  .sort({ updatedAt: -1 })
  .limit(5)

db.candidate_embeddings
  .find()
  .sort({ updatedAt: -1 })
  .limit(5)

db.match_results
  .find()
  .sort({ generatedAt: -1 })
  .limit(20)
```

---

# 14. Qdrant verification

```powershell
Invoke-RestMethod `
  http://localhost:6333/collections
```

Expected collection:

```text
job_vectors_v1
```

---

# 15. Definition of done

Một CV → matching flow chỉ được coi là pass khi:

```text
CV upload succeeds
parser returns valid profile
candidate profile persisted
candidate embedding READY
matching search succeeds
results persisted
ranking manually looks reasonable
```