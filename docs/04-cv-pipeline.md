# AutoJob Technical Guide

Tài liệu này mô tả kiến trúc, pipeline, API, data storage, cách chạy local, cách verify và các lỗi thường gặp của repository AutoJob hiện tại.

---

## 1. Phạm vi hiện tại

Repository đang sử dụng kiến trúc:

```text
Spring Boot Modular Monolith
+
Python FastAPI services
+
MongoDB, Qdrant, MinIO
+
Docker Compose cho local infrastructure
```

Java backend deploy thành một process duy nhất là:

```text
autojob-app
```

Các module trong backend giữ boundary riêng nhưng giao tiếp hiện tại chủ yếu qua:

* Spring dependency injection.
* Spring Application Event.
* MongoDB repository.
* HTTP call từ Java sang embedding service.

Hiện chưa sử dụng RabbitMQ hoặc Kafka.

---

## 2. Module map

### 2.1. `backend/common/common-dtos`

Đường dẫn:

```text
backend/common/common-dtos/
```

Chứa DTO hoặc enum dùng chung giữa các module.

Hiện có:

```text
ApplyType
```

Các giá trị:

```text
DETAIL_PAGE
DETAIL_PAGE_APPLY_BUTTON
EXTERNAL_COMPANY_SITE
EMAIL
UNKNOWN
```

---

### 2.2. `backend/common/common-events`

Đường dẫn:

```text
backend/common/common-events/
```

Chứa internal event:

```text
JobRawCollectedEvent
JobNormalizedReadyEvent
```

Các event hiện là Spring Application Event chạy trong cùng JVM, không phải distributed event.

---

### 2.3. `backend/modules/job-crawler`

Trách nhiệm:

* Crawl hoặc parse job source.
* Tạo `RawJob`.
* Tạo fingerprint.
* Upsert `raw_jobs`.
* Publish `JobRawCollectedEvent`.
* Quản lý source discovery skeleton.

Không chịu trách nhiệm:

* Normalize business field.
* Tạo embedding.
* Match CV.
* Tự động apply.

Mock crawler được trigger bằng:

```http
POST /api/admin/crawlers/mock/run
```

Live parser hiện có cho:

```text
ITVIEC
JOBOKO
TOPDEV
VIECLAM24H
MOCK
```

Tuy nhiên chỉ mock source đã có Camel crawl route được nối vào admin API.

---

### 2.4. `backend/modules/job-normalizer`

Trách nhiệm:

* Nhận `JobRawCollectedEvent`.
* Load raw job.
* Normalize text.
* Normalize skill.
* Normalize location.
* Normalize salary.
* Normalize experience.
* Normalize seniority.
* Normalize job type.
* Parse posted/deadline date.
* Chọn apply URL.
* Build `embeddingText`.
* Lưu `normalized_jobs`.
* Publish `JobNormalizedReadyEvent`.

Normalization output được version bằng:

```text
normalizationVersion
```

Mặc định:

```text
rule-v1
```

Unique business key:

```text
rawJobId + normalizationVersion
```

---

### 2.5. `backend/modules/job-embedding`

Trách nhiệm:

* Nhận `JobNormalizedReadyEvent`.
* Load `NormalizedJob`.
* Tính hash của `embeddingText`.
* Gọi FastAPI embedding service.
* Validate embedding version và dimension.
* Lưu metadata vào `job_embeddings`.
* Ensure Qdrant collection.
* Upsert vector vào Qdrant.

Embedding metadata được version bằng:

```text
embeddingVersion
```

Unique business key:

```text
normalizedJobId + embeddingVersion
```

---

### 2.6. `backend/modules/auth`

Trách nhiệm:

* Register.
* Login.
* JWT access token.
* Refresh token rotation.
* Logout.
* Role.
* BCrypt password hashing.
* Rate limiting.
* CORS.
* Public API mode cho local development.

MongoDB collections:

```text
users
refresh_tokens
```

---

### 2.7. `backend/modules/cv`

Trách nhiệm hiện tại:

* Nhận multipart upload.
* Validate file.
* Tính SHA-256.
* Lưu object vào MinIO.
* Lưu metadata vào `raw_cvs`.
* Trả metadata qua REST API.

Chưa chịu trách nhiệm:

* Gọi CV parser.
* Lưu candidate profile.
* Tạo candidate embedding.
* Trigger matching.

---

### 2.8. `backend/modules/matching`

Thư mục đã tồn tại:

```text
backend/modules/matching/
```

Nhưng hiện chưa được khai báo trong:

```text
backend/pom.xml
```

và chưa được dependency bởi:

```text
backend/autojob-app/pom.xml
```

Do đó matching module chưa nằm trong runtime application.

---

### 2.9. `ai-services/embedding-service`

FastAPI endpoint:

```http
POST /api/v1/embeddings
```

Health:

```http
GET /health
GET /ready
```

Hỗ trợ hai provider:

```text
sentence-transformer
fake
```

Default real model:

```text
intfloat/multilingual-e5-small
```

Vector dimension:

```text
384
```

Normalization:

```text
L2
```

---

### 2.10. `ai-services/cv-parser-service`

FastAPI endpoint:

```http
POST /api/v1/cv/parse
```

Health:

```http
GET /health
GET /ready
```

Parser hiện có khả năng:

* Download object từ MinIO.
* Extract text từ PDF.
* Extract text từ DOCX.
* Extract text từ DOC qua `antiword`.
* Normalize text.
* Detect section.
* Parse identity và contact.
* Parse skill.
* Parse work experience.
* Parse education.
* Parse project.
* Parse certification và license.
* Parse language.
* Tính experience years.
* Suy luận seniority.
* Tính parse quality.
* Trả warnings.

Service chưa được thêm vào root `docker-compose.yml` và Java backend chưa có client gọi service này.

---

## 3. Job pipeline hiện tại

### 3.1. Sequence

```text
POST /api/admin/crawlers/mock/run
    |
    v
Apache Camel MockJobCrawlerRoute
    |
    v
RawJobService.upsertSeen()
    |
    +--> MongoDB raw_jobs
    |
    +--> JobRawCollectedEvent
             |
             v
       JobRawCollectedEventListener
             |
             v
       JobNormalizationService
             |
             +--> MongoDB normalized_jobs
             |
             +--> JobNormalizedReadyEvent
                       |
                       v
             JobNormalizedReadyEventListener
                       |
                       v
             JobEmbeddingService
                       |
                       +--> FastAPI embedding-service
                       +--> MongoDB job_embeddings
                       +--> Qdrant job_vectors_v1
```

Spring event listener hiện chạy synchronous trong cùng application thread, ngoại trừ việc embedding listener chủ động không propagate lỗi embedding về normalizer.

Điều này có nghĩa:

* Lỗi normalization có thể làm crawler request lỗi.
* Lỗi embedding được log nhưng normalized job vẫn tồn tại.
* Chưa có retry queue bền vững.
* Restart application có thể cần manual rebuild embedding cho record lỗi.

---

## 4. Idempotency

### Raw job

Raw job sử dụng:

```text
fingerprint
```

Fingerprint có unique index.

Ví dụ:

```text
VIECLAM24H:200847455
MOCK:job-001
```

Khi crawler gặp lại cùng fingerprint:

* Không insert document mới.
* Update business fields.
* Giữ `firstSeenAt`.
* Giữ `expiresAt`.
* Update `lastSeenAt`.
* Update `collectedAt`.
* Publish event lại để normalizer kiểm tra thay đổi.

### Normalized job

Unique index:

```text
rawJobId + normalizationVersion
```

`rawContentHash` được dùng để xác định business content có thực sự đổi hay không.

### Embedding

Unique index:

```text
normalizedJobId + embeddingVersion
```

`textHash` được dùng để bỏ qua embedding nếu text không đổi.

Qdrant point ID được tạo ổn định từ:

```text
normalizedJobId + embeddingVersion
```

---

## 5. Raw job retention

`RawJob` có TTL index trên:

```text
expiresAt
```

Mặc định:

```text
firstSeenAt + 30 ngày
```

Crawler update không refresh `expiresAt`.

Sau normalization thành công, raw payload lớn có thể bị purge:

```text
rawHtml
rawText
```

Các business field vẫn được giữ để trace.

---

## 6. Environment variables quan trọng

### MongoDB

```dotenv
MONGO_PORT=27018
MONGO_ROOT_USERNAME=root
MONGO_ROOT_PASSWORD=password
MONGO_DATABASE=autojob
```

### Qdrant

```dotenv
QDRANT_HTTP_PORT=6333
QDRANT_GRPC_PORT=6334
QDRANT_BASE_URL=http://localhost:6333
QDRANT_JOB_COLLECTION=job_vectors_v1
QDRANT_VECTOR_DIMENSION=384
QDRANT_DISTANCE=Cosine
```

### Embedding

```dotenv
EMBEDDING_SERVICE_PORT=8002
EMBEDDING_SERVICE_BASE_URL=http://localhost:8002
EMBEDDING_PROVIDER=sentence-transformer
EMBEDDING_EXPECTED_DIMENSION=384
EMBEDDING_PREPROCESSING_VERSION=prep-v1
EMBEDDING_NORMALIZATION_STRATEGY=l2
```

### MinIO

```dotenv
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_ENDPOINT=http://localhost:9000
MINIO_BUCKET_CVS=autojob-cvs
```

Lưu ý:

* Java MinIO client dùng endpoint có scheme:

  ```text
  http://localhost:9000
  ```

* Python CV parser yêu cầu endpoint không có scheme:

  ```text
  127.0.0.1:9000
  ```

### Authentication

```dotenv
AUTH_PUBLIC_API_MODE=true
AUTH_JWT_ISSUER=autojob-app
JWT_SECRET_BASE64=<base64-secret>
AUTH_ACCESS_TOKEN_TTL=15m
AUTH_REFRESH_TOKEN_TTL=30d
FRONTEND_ORIGIN=http://localhost:5173
```

---

## 7. Chạy local chi tiết

### 7.1. Infrastructure

```bash
cp .env.example .env

docker compose --env-file .env up -d
```

Xem log:

```bash
docker compose --env-file .env logs -f
```

Xem log riêng:

```bash
docker compose --env-file .env logs -f embedding-service
```

```bash
docker compose --env-file .env logs -f mongo
```

```bash
docker compose --env-file .env logs -f qdrant
```

```bash
docker compose --env-file .env logs -f minio
```

Dừng service:

```bash
docker compose --env-file .env down
```

Xóa cả local data:

```bash
docker compose --env-file .env down -v
```

Cẩn thận: `-v` xóa MongoDB, Qdrant, MinIO và model cache.

---

### 7.2. Backend

```bash
cd backend

chmod +x mvnw

./mvnw -pl autojob-app -am spring-boot:run \
  -Dspring-boot.run.profiles=local
```

Build:

```bash
./mvnw clean package
```

Build bỏ test:

```bash
./mvnw clean package -DskipTests
```

File JAR dự kiến:

```text
backend/autojob-app/target/autojob-app-0.1.0-SNAPSHOT.jar
```

Run JAR:

```bash
java -jar \
  backend/autojob-app/target/autojob-app-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local
```

---

### 7.3. Embedding service ngoài Docker

```bash
cd ai-services/embedding-service

python3.12 -m venv .venv
source .venv/bin/activate

pip install -r requirements.txt

EMBEDDING_PROVIDER=fake \
EMBEDDING_EXPECTED_DIMENSION=384 \
python -m uvicorn app.main:app \
  --host 0.0.0.0 \
  --port 8002
```

Kiểm tra:

```bash
curl http://localhost:8002/health | jq
curl http://localhost:8002/ready | jq
```

Test embedding:

```bash
curl -X POST \
  http://localhost:8002/api/v1/embeddings \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Senior Java Spring Boot developer with MongoDB experience"
  }' | jq
```

---

### 7.4. CV parser service ngoài Docker

Cài `antiword` trên Ubuntu/Debian:

```bash
sudo apt-get update
sudo apt-get install -y antiword
```

Chạy service:

```bash
cd ai-services/cv-parser-service

python3.12 -m venv .venv
source .venv/bin/activate

pip install -r requirements.txt

MINIO_ENDPOINT=127.0.0.1:9000 \
MINIO_ACCESS_KEY=minioadmin \
MINIO_SECRET_KEY=minioadmin \
MINIO_BUCKET_CVS=autojob-cvs \
CV_PARSER_VERSION=rule-v1 \
python -m uvicorn app.main:app \
  --host 0.0.0.0 \
  --port 8003
```

Kiểm tra:

```bash
curl http://localhost:8003/health | jq
curl http://localhost:8003/ready | jq
```

`/ready` chỉ trả `UP` khi:

* MinIO bucket truy cập được.
* `antiword` khả dụng.

---

## 8. Smoke test CV parser thủ công

### Bước 1 — Upload CV qua Java

```bash
curl -X POST \
  -F "file=@./sample-cv.pdf" \
  http://localhost:8080/api/cvs | tee /tmp/raw-cv.json | jq
```

Lấy ID:

```bash
RAW_CV_ID=$(jq -r '.id' /tmp/raw-cv.json)
echo "${RAW_CV_ID}"
```

### Bước 2 — Query object metadata trong MongoDB

```bash
docker compose --env-file .env exec mongo \
  mongosh \
  -u root \
  -p password \
  --authenticationDatabase admin \
  autojob \
  --quiet \
  --eval "JSON.stringify(db.raw_cvs.findOne({_id: '${RAW_CV_ID}'}))"
```

Cần lấy các field:

```text
bucket
objectKey
originalFilename
contentType
```

### Bước 3 — Gọi CV parser

Ví dụ:

```bash
curl -X POST \
  http://localhost:8003/api/v1/cv/parse \
  -H "Content-Type: application/json" \
  -d '{
    "rawCvId": "'"${RAW_CV_ID}"'",
    "bucket": "autojob-cvs",
    "objectKey": "raw/2026/08/02/'"${RAW_CV_ID}"'/sample-cv.pdf",
    "originalFilename": "sample-cv.pdf",
    "contentType": "application/pdf"
  }' | jq
```

Phải dùng `objectKey` thật lấy từ MongoDB, không tự đoán ngày.

Parser response gồm:

```json
{
  "rawCvId": "...",
  "parserVersion": "rule-v1",
  "extractedTextLength": 1234,
  "detectedLanguage": "VI",
  "profile": {
    "fullName": "...",
    "contact": {},
    "skills": [],
    "workExperiences": [],
    "educations": [],
    "experienceYears": 3.5,
    "seniority": "MID",
    "rawText": "...",
    "parseQuality": {}
  },
  "warnings": []
}
```

Response này hiện chưa được Java lưu vào `candidate_profiles`.

---

## 9. Backend API

### 9.1. Auth

#### Register

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "email": "candidate@example.com",
  "password": "ChangeMe123!",
  "displayName": "Demo Candidate"
}
```

#### Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "candidate@example.com",
  "password": "ChangeMe123!"
}
```

#### Refresh

```http
POST /api/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "<refresh-token>"
}
```

#### Logout

```http
POST /api/auth/logout
Content-Type: application/json
```

```json
{
  "refreshToken": "<refresh-token>"
}
```

#### Current user

```http
GET /api/auth/me
Authorization: Bearer <access-token>
```

---

### 9.2. CV

#### Upload

```http
POST /api/cvs
Content-Type: multipart/form-data
```

Form field:

```text
file
```

#### Get metadata

```http
GET /api/cvs/{rawCvId}
```

Public response không trả:

```text
bucket
objectKey
uploadedFromIp
lastError
```

Đây là lựa chọn hợp lý để không expose storage internals.

---

### 9.3. Raw jobs

```http
GET /api/raw-jobs?limit=20
```

`limit` được clamp trong khoảng:

```text
1..100
```

Manual normalize:

```http
POST /api/raw-jobs/{rawJobId}/normalize?force=false
```

---

### 9.4. Normalized jobs

```http
GET /api/normalized-jobs
```

Query:

```text
page
size
sourceCode
normalizationVersion
```

Ví dụ:

```bash
curl \
  "http://localhost:8080/api/normalized-jobs?page=0&size=20&sourceCode=MOCK" \
  | jq
```

Detail:

```http
GET /api/normalized-jobs/{id}
```

Batch renormalization:

```http
POST /api/admin/job-normalization/renormalize
```

Request body phụ thuộc batch service. Khi chưa cần filter đặc biệt, có thể gửi body rỗng:

```bash
curl -X POST \
  http://localhost:8080/api/admin/job-normalization/renormalize \
  -H "Content-Type: application/json" \
  -d '{}' | jq
```

---

### 9.5. Job embedding

Get latest:

```http
GET /api/job-embeddings/{normalizedJobId}
```

Rebuild:

```http
POST /api/admin/job-embeddings/{normalizedJobId}/rebuild?force=true
```

Ví dụ:

```bash
curl -X POST \
  "http://localhost:8080/api/admin/job-embeddings/${NORMALIZED_JOB_ID}/rebuild?force=true" \
  | jq
```

---

### 9.6. Source Discovery

Create source:

```bash
curl -X POST \
  http://localhost:8080/api/admin/source-discovery/website-sources \
  -H "Content-Type: application/json" \
  -d '{
    "sourceCode": "ACME",
    "domain": "https://example.com/"
  }' | jq
```

Run discovery:

```bash
WEBSITE_SOURCE_ID="<id>"

curl -X POST \
  "http://localhost:8080/api/admin/source-discovery/website-sources/${WEBSITE_SOURCE_ID}/run" \
  | jq
```

Get results:

```bash
curl \
  "http://localhost:8080/api/admin/source-discovery/website-sources/${WEBSITE_SOURCE_ID}/results" \
  | jq
```

Hiện tại service chỉ tạo candidate URL:

```text
/careers
/jobs
/tuyen-dung
/viec-lam
```

Nó chưa:

* Fetch URL.
* Kiểm tra HTTP status.
* Parse robots.txt.
* Parse sitemap.
* Scan homepage navigation.
* Detect ATS.
* Approve result thành crawler source.

---

## 10. MongoDB collections

### `raw_jobs`

Source of truth cho crawler output.

Các field quan trọng:

```text
_id
sourceCode
sourceJobId
fingerprint
detailUrl
applyUrl
applyType
title
companyName
salaryText
locationText
experienceText
skills
descriptionText
requirementsText
benefitsText
firstSeenAt
lastSeenAt
expiresAt
collectedAt
```

### `normalized_jobs`

Dữ liệu job đã chuẩn hóa.

Các field quan trọng:

```text
_id
rawJobId
sourceCode
sourceJobId
sourceFingerprint
rawContentHash
title
companyName
skills
locations
salaryMin
salaryMax
currency
experienceMin
experienceMax
seniority
jobType
embeddingText
normalizationVersion
postedAt
deadlineAt
normalizedAt
```

### `job_embeddings`

Metadata của embedding, không lưu toàn bộ vector.

```text
normalizedJobId
normalizationVersion
modelName
modelRevision
embeddingVersion
textHash
dimension
normalized
qdrantCollection
qdrantPointId
status
lastError
embeddedAt
```

### `raw_cvs`

Metadata file CV.

```text
_id
ownerUserId
bucket
objectKey
originalFilename
extension
contentType
sizeBytes
sha256
status
lastError
uploadedFromIp
uploadedAt
```

### `users`

User account.

### `refresh_tokens`

Refresh token session.

### `website_sources`

Website đang chờ hoặc đã chạy discovery.

### `source_discovery_results`

Candidate URL do source discovery tạo.

### Collections chưa có runtime flow

```text
candidate_profiles
candidate_embeddings
match_results
crawler_sources
```

---

## 11. Qdrant

Collection mặc định:

```text
job_vectors_v1
```

Config:

```text
size = 384
distance = Cosine
```

Payload tối thiểu dự kiến:

```json
{
  "jobId": "<normalized-job-id>",
  "sourceCode": "MOCK",
  "embeddingVersion": "<version>"
}
```

Không lưu full job detail trong Qdrant.

### Kiểm tra collection

```bash
curl \
  http://localhost:6333/collections/job_vectors_v1 | jq
```

### Scroll point

```bash
curl -X POST \
  http://localhost:6333/collections/job_vectors_v1/points/scroll \
  -H "Content-Type: application/json" \
  -d '{
    "limit": 20,
    "with_payload": true,
    "with_vector": false
  }' | jq
```

### Xóa collection khi đổi dimension

Chỉ dùng local:

```bash
curl -X DELETE \
  http://localhost:6333/collections/job_vectors_v1
```

Sau đó rebuild embedding.

---

## 12. MinIO

Bucket:

```text
autojob-cvs
```

Bucket được tạo bởi container:

```text
minio-init
```

Bucket không public.

Kiểm tra health:

```bash
curl \
  http://localhost:9000/minio/health/live
```

Xem log init:

```bash
docker compose --env-file .env logs minio-init
```

---

## 13. Matching design mục tiêu

Matching không scan toàn bộ job trong Java.

Flow:

```text
Candidate embedding
    |
    v
Qdrant search topK=50 hoặc 100
    |
    v
Lấy normalized job theo danh sách jobId
    |
    v
Tính rule scores
    |
    v
Sort finalScore
    |
    v
Lưu match_results
```

Khi dùng real embedding:

```text
finalScore =
    vectorScore    * 0.50
  + skillScore     * 0.30
  + seniorityScore * 0.10
  + locationScore  * 0.05
  + freshnessScore * 0.05
```

Khi dùng fake embedding:

```text
finalScore =
    vectorScore    * 0.30
  + skillScore     * 0.45
  + seniorityScore * 0.10
  + locationScore  * 0.10
  + freshnessScore * 0.05
```

Mọi component score nên normalize về:

```text
0.0..1.0
```

---

## 14. Troubleshooting

### 14.1. `./mvnw: Permission denied`

Fix:

```bash
chmod +x backend/mvnw
```

Hoặc:

```bash
cd backend
bash ./mvnw test
```

---

### 14.2. Maven Wrapper không tải được Maven

Triệu chứng:

```text
Failed to fetch apache-maven-*.zip
```

Nguyên nhân:

* Không có Internet.
* Proxy hoặc firewall chặn Maven Central.
* DNS lỗi.

Fix:

* Kiểm tra Internet.
* Cấu hình Maven proxy trong `~/.m2/settings.xml`.
* Cài Maven local và dùng `mvn`.
* Cache dependency trong CI hoặc image build.

---

### 14.3. Embedding service ở trạng thái `starting`

Nguyên nhân thường gặp:

* Sentence Transformer đang tải model.
* Không có Internet.
* Model revision sai.
* Thiếu RAM.
* Hugging Face bị timeout.

Xem log:

```bash
docker compose --env-file .env logs -f embedding-service
```

Dùng fake provider để smoke test:

```dotenv
EMBEDDING_PROVIDER=fake
EMBEDDING_EXPECTED_VERSION=autojob/fake-sha256@deterministic-v1|prep-v1|l2
```

Restart:

```bash
docker compose --env-file .env up -d --build embedding-service
```

---

### 14.4. Embedding version mismatch

Triệu chứng:

```text
Embedding version mismatch
```

Nguyên nhân:

Backend đang expect version của real model nhưng service chạy fake provider, hoặc ngược lại.

Fake:

```dotenv
EMBEDDING_PROVIDER=fake
EMBEDDING_EXPECTED_VERSION=autojob/fake-sha256@deterministic-v1|prep-v1|l2
```

Real:

```dotenv
EMBEDDING_PROVIDER=sentence-transformer
EMBEDDING_EXPECTED_VERSION=intfloat/multilingual-e5-small@c007d7ef6fd86656326059b28395a7a03a7c5846|prep-v1|l2
```

---

### 14.5. Qdrant dimension mismatch

Triệu chứng:

```text
expected dimension 384
existing collection has another dimension
```

Fix local:

```bash
curl -X DELETE \
  http://localhost:6333/collections/job_vectors_v1
```

Đảm bảo:

```dotenv
QDRANT_VECTOR_DIMENSION=384
EMBEDDING_EXPECTED_DIMENSION=384
```

Sau đó rebuild embedding.

---

### 14.6. Có raw job nhưng không có normalized job

Kiểm tra backend log:

```text
Received JobRawCollectedEvent
Failed to handle JobRawCollectedEvent
```

Manual normalize:

```bash
curl -X POST \
  "http://localhost:8080/api/raw-jobs/${RAW_JOB_ID}/normalize?force=true" \
  | jq
```

Kiểm tra MongoDB:

```javascript
db.raw_jobs.findOne({_id: "<raw-job-id>"})
```

Các field tối thiểu nên có:

```text
fingerprint
title
sourceCode
```

---

### 14.7. Có normalized job nhưng không có Qdrant point

Embedding listener không propagate lỗi về normalizer, nên normalized job vẫn có thể tồn tại.

Kiểm tra:

```javascript
db.job_embeddings
  .find({normalizedJobId: "<id>"})
  .pretty()
```

Xem:

```text
status
lastError
embeddingVersion
qdrantPointId
```

Manual rebuild:

```bash
curl -X POST \
  "http://localhost:8080/api/admin/job-embeddings/${NORMALIZED_JOB_ID}/rebuild?force=true" \
  | jq
```

---

### 14.8. MinIO upload lỗi

Kiểm tra:

```bash
curl \
  http://localhost:9000/minio/health/live
```

```bash
docker compose --env-file .env logs minio
docker compose --env-file .env logs minio-init
```

Đảm bảo bucket tồn tại:

```text
autojob-cvs
```

Đảm bảo Java endpoint:

```dotenv
MINIO_ENDPOINT=http://localhost:9000
```

---

### 14.9. CV parser `/ready` trả `DOWN`

Kiểm tra response:

```bash
curl http://localhost:8003/ready | jq
```

Các nguyên nhân:

* Không kết nối được MinIO.
* Bucket không tồn tại.
* Sai access key.
* Sai secret key.
* `antiword` chưa cài.

Python parser endpoint phải không có scheme:

```dotenv
MINIO_ENDPOINT=127.0.0.1:9000
```

Không dùng:

```text
http://127.0.0.1:9000
```

---

### 14.10. Upload DOC nhưng parser lỗi

File `.doc` cũ cần `antiword`.

Linux:

```bash
sudo apt-get install antiword
```

Trên Windows, ưu tiên test PDF hoặc DOCX trước.

Không đổi extension thủ công từ `.docx` sang `.doc`.

---

### 14.11. API admin trả `403`

Khi:

```dotenv
AUTH_PUBLIC_API_MODE=false
```

các endpoint:

```text
/api/admin/**
```

yêu cầu JWT có role:

```text
ADMIN
```

Local development có thể tạm dùng:

```dotenv
AUTH_PUBLIC_API_MODE=true
```

Không dùng chế độ này trong production.

---

### 14.12. Frontend bị CORS

Kiểm tra:

```dotenv
FRONTEND_ORIGIN=http://localhost:5173
```

Origin phải khớp chính xác protocol, host và port.

Ví dụ `http://127.0.0.1:5173` khác với:

```text
http://localhost:5173
```

---

### 14.13. Mock crawler trả `insertedCount: 0`

Đây có thể là hành vi đúng.

Crawler dùng fingerprint để upsert. Nếu job đã tồn tại:

* Document được update.
* `totalRawJobs` không tăng.
* `insertedCount` bằng `0`.
* Normalization event vẫn có thể được publish.

---

### 14.14. Source Discovery trả URL không tồn tại

Source Discovery hiện chỉ là skeleton và chưa probe URL thật.

Nó tạo common path dựa trên domain, không đảm bảo endpoint trả `200`.

Không dùng kết quả hiện tại để tự động crawl production.

---

## 15. Deployment VPS hiện tại

Root Compose hiện chỉ chạy infrastructure và Python embedding service.

Nó chưa chạy:

* Java backend.
* CV parser service.
* Frontend.
* Nginx reverse proxy.

Trước khi deploy MVP, cần bổ sung:

```text
backend/Dockerfile
frontend/web-app/Dockerfile
infra/nginx/nginx.conf
cv-parser-service trong docker-compose.yml
autojob-app trong docker-compose.yml
frontend trong docker-compose.yml
```

Network production đề xuất:

```text
Internet
   |
   v
Nginx :80/:443
   |
   +--> frontend
   |
   +--> autojob-app:8080

Internal-only:
- mongo:27017
- qdrant:6333
- minio:9000
- embedding-service:8002
- cv-parser-service:8003
```

Không expose trực tiếp MongoDB, Qdrant hoặc MinIO API ra public Internet.

---

## 16. Production checklist

* [ ] Đổi toàn bộ password mặc định.
* [ ] Tạo JWT secret mới.
* [ ] Đặt `AUTH_PUBLIC_API_MODE=false`.
* [ ] Bật HTTPS.
* [ ] Chỉ expose Nginx.
* [ ] Backup MongoDB.
* [ ] Backup MinIO.
* [ ] Backup hoặc có kế hoạch rebuild Qdrant.
* [ ] Pin image version.
* [ ] Thêm container resource limit.
* [ ] Thêm log rotation.
* [ ] Thêm health check cho Java backend.
* [ ] Thêm startup order phù hợp.
* [ ] Không phụ thuộc `depends_on` thay cho application retry.
* [ ] Thêm migration procedure cho Qdrant collection version.
* [ ] Thêm retention policy cho CV.
* [ ] Không log raw CV text hoặc JWT.
* [ ] Rate limit upload và auth.
* [ ] Kiểm tra robots.txt trước khi bật live crawler.
