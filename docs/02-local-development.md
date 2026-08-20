# Local Development

## 1. Recommended workflow

Local development ưu tiên Docker Compose.

Không cần chạy:

```text
java -jar
mvn spring-boot:run
```

cho smoke/integration flow thông thường.

---

## 2. Setup

Từ repo root:

```powershell
Copy-Item .env.example .env
```

Đảm bảo:

```dotenv
CV_PARSER_VERSION=rule-v2
CV_PARSER_EXPECTED_VERSION=rule-v2
```

Start:

```powershell
docker compose --env-file .env up -d --build
```

---

## 3. Services

```text
autojob-app           :8080
embedding-service     :8002
cv-parser-service     :8003

mongo                 :27018 host
qdrant                :6333
minio                 :9000
minio console         :9001

mock-job-site         :18080
```

---

## 4. Check containers

```powershell
docker compose --env-file .env ps
```

App health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

CV parser:

```powershell
Invoke-RestMethod http://localhost:8003/ready
```

Embedding:

```powershell
Invoke-RestMethod http://localhost:8002/ready
```

---

## 5. Build only CV parser

Khi chỉ sửa:

```text
ai-services/cv-parser-service
```

run:

```powershell
docker compose --env-file .env `
  up -d --build cv-parser-service
```

Nếu Java app phải reload shared taxonomy/config thì restart:

```powershell
docker compose --env-file .env restart autojob-app
```

---

## 6. Build backend image

```powershell
docker compose --env-file .env `
  up -d --build autojob-app
```

---

## 7. Logs

App:

```powershell
docker compose --env-file .env `
  logs --tail=200 autojob-app
```

CV parser:

```powershell
docker compose --env-file .env `
  logs --tail=200 cv-parser-service
```

Embedding:

```powershell
docker compose --env-file .env `
  logs --tail=200 embedding-service
```

Mongo:

```powershell
docker compose --env-file .env `
  logs --tail=100 mongo
```

---

## 8. Smoke job crawler

Mock:

```powershell
Invoke-RestMethod `
  -Method POST `
  http://localhost:8080/api/admin/crawlers/mock/run
```

Live:

```text
POST /api/admin/crawlers/live/ITVIEC/run?limit=15
POST /api/admin/crawlers/live/JOBOKO/run?limit=15
POST /api/admin/crawlers/live/TOPDEV/run?limit=15
POST /api/admin/crawlers/live/VIECLAM24H/run?limit=15
```

Maximum live limit:

```text
50
```

---

## 9. Inspect jobs

Raw:

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/raw-jobs?limit=20"
```

Normalized:

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/normalized-jobs?page=0&size=20"
```

---

## 10. Real CV smoke test

Script:

```text
scripts/test-cv-parse-embedding.ps1
```

Set:

```powershell
$CvPath = "D:\test-data\cv2.pdf"
```

Run:

```powershell
powershell -ExecutionPolicy Bypass `
  -File .\scripts\test-cv-parse-embedding.ps1
```

Flow:

```text
CV
→ upload
→ parse
→ candidate_profiles
→ candidate_embeddings
```

---

## 11. Matching smoke test

Sau khi có candidate profile id:

```powershell
$candidateProfileId = "..."
```

Run:

```powershell
Invoke-RestMethod `
  -Method POST `
  "http://localhost:8080/api/matching/candidates/$candidateProfileId"
```

Force:

```powershell
Invoke-RestMethod `
  -Method POST `
  "http://localhost:8080/api/matching/candidates/$candidateProfileId?force=true"
```

Read current:

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/matching/candidates/$candidateProfileId"
```

---

## 12. Mongo shell

```powershell
docker exec -it autojob-mongo `
  mongosh `
  -u root `
  -p password `
  --authenticationDatabase admin `
  autojob
```

Useful:

```javascript
db.raw_cvs.find().sort({ uploadedAt: -1 }).limit(3)

db.candidate_profiles
  .find()
  .sort({ updatedAt: -1 })
  .limit(3)

db.candidate_embeddings
  .find()
  .sort({ updatedAt: -1 })
  .limit(3)

db.match_results
  .find()
  .sort({ generatedAt: -1 })
  .limit(20)
```

---

## 13. Qdrant

Collections:

```powershell
Invoke-RestMethod `
  http://localhost:6333/collections
```

Expected:

```text
job_vectors_v1
```

---

## 14. Version compatibility

Current:

```text
normalizer              rule-v4
cv parser               rule-v2
job text                job-text-v2
candidate text          candidate-text-v1
matching                hybrid-v6-balanced-r4
embedding dimension     384
```

Matching rejects incompatible upstream versions.

---

## 15. Important Compose caveat

`.env.example` hiện đúng:

```dotenv
CV_PARSER_VERSION=rule-v2
CV_PARSER_EXPECTED_VERSION=rule-v2
```

Nhưng `docker-compose.yml` vẫn có một số fallback:

```text
rule-v1
```

Do đó local command chuẩn luôn là:

```powershell
docker compose --env-file .env ...
```

Không xóa `.env` khi test parser.