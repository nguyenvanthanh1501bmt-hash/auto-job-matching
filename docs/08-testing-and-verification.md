# Testing và verification

## Phạm vi

Repository hiện có ba lớp kiểm tra:

```text
Java unit/component tests
Python service tests
Local end-to-end smoke test
```

Local smoke test đầy đủ nhất hiện nay là job pipeline:

```text
Mock site
→ raw_jobs
→ normalized_jobs
→ job_embeddings
→ Qdrant
```

CV pipeline chỉ verify được đến:

```text
Upload CV
→ MinIO
→ raw_cvs
```

`cv-parser-service` có test riêng nhưng chưa được Java backend gọi.

## Chuẩn bị môi trường

Từ thư mục gốc:

```bash
cp .env.example .env

docker compose --env-file .env up -d --build
```

Kiểm tra:

```bash
docker compose --env-file .env ps

curl -f http://localhost:18080/jobs.html
curl -f http://localhost:9000/minio/health/live
curl -f http://localhost:8002/ready
curl -f http://localhost:6333/collections
```

Khởi động backend:

```bash
cd backend

bash ./mvnw \
  -pl autojob-app \
  -am \
  spring-boot:run \
  -Dspring-boot.run.profiles=local
```

Health check:

```bash
curl -s http://localhost:8080/actuator/health | jq
```

## Chạy backend tests

### Toàn bộ Maven reactor

```bash
cd backend
bash ./mvnw test
```

Lần đầu Maven Wrapper cần tải Maven 3.9.9 và dependencies từ Maven Central.

### Các module job pipeline

```bash
cd backend

bash ./mvnw \
  -pl modules/job-crawler,modules/job-normalizer,modules/job-embedding \
  -am \
  test
```

Các test hiện có nội dung thực tập trung vào:

* Crawler parser fixture.
* Raw job fingerprint và idempotency.
* Raw payload purge.
* Salary, skill, location, seniority và date normalization.
* `embeddingText`.
* Normalization event listener.
* Embedding HTTP response validation.
* Embedding idempotency.
* Qdrant collection validation và point upsert.

### Trạng thái test theo module

| Module           | Trạng thái test                                     |
| ---------------- | --------------------------------------------------- |
| `job-crawler`    | Có test thực                                        |
| `job-normalizer` | Có test thực                                        |
| `job-embedding`  | Có test thực                                        |
| `cv`             | Có năm file test nhưng hiện đều rỗng                |
| `auth`           | Chưa có test source                                 |
| `matching`       | Chưa có implementation hoặc test                    |
| `autojob-app`    | Chưa có integration test khởi động toàn application |

Việc `mvn test` thành công chưa chứng minh CV upload, authentication hoặc full application đã được kiểm tra đầy đủ.

## Chạy Embedding Service tests

Từ thư mục service:

```bash
cd ai-services/embedding-service

python3.12 -m venv .venv
source .venv/bin/activate

python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

### Fake provider tests

Không tải model thật:

```bash
pytest -m "not model"
```

Các test kiểm tra:

* `/health`.
* `/ready`.
* Vector dimension.
* Deterministic output.
* SHA-256 `textHash`.
* L2 normalization.
* Validation với text rỗng.

### Real model tests

```bash
pytest -m model
```

Test này tải model:

```text
intfloat/multilingual-e5-small
```

và kiểm tra:

* Model load thành công.
* Vector 384 chiều.
* Vector hữu hạn và L2-normalized.
* Kết quả ổn định với cùng input.
* Nội dung Java tương tự bằng tiếng Việt và tiếng Anh gần nhau hơn nội dung không liên quan.

Lần chạy đầu cần kết nối Internet và có thể dùng nhiều RAM hơn fake provider.

Chạy tất cả:

```bash
pytest
```

## Chạy CV Parser tests

`requirements.txt` của CV parser hiện chứa runtime dependencies nhưng chưa chứa đầy đủ test dependencies.

Chuẩn bị:

```bash
cd ai-services/cv-parser-service

python3.12 -m venv .venv
source .venv/bin/activate

python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m pip install pytest httpx reportlab
```

### Unit tests

Không cần MinIO thật:

```bash
pytest -m "not integration"
```

Các test kiểm tra:

* PDF, DOC và DOCX extraction.
* PDF lỗi hoặc không có text.
* DOCX archive safety.
* Multi-column warning.
* Contact và identity parsing.
* Section detection.
* Skill taxonomy.
* Work experience.
* Education và certification.
* Language và seniority inference.
* FastAPI request validation.

Các DOC unit test mock lệnh `antiword`, vì vậy không chứng minh binary thật đang hoạt động.

### MinIO integration test

Khởi động MinIO:

```bash
docker compose --env-file .env \
  up -d minio minio-init
```

Chạy integration test:

```bash
cd ai-services/cv-parser-service
source .venv/bin/activate

RUN_MINIO_INTEGRATION=true \
CV_TEST_MINIO_ENDPOINT=127.0.0.1:9000 \
CV_TEST_MINIO_ACCESS_KEY=minioadmin \
CV_TEST_MINIO_SECRET_KEY=minioadmin \
CV_TEST_MINIO_BUCKET=autojob-cvs \
pytest -m integration
```

Test tự tạo CV fixture, upload vào MinIO, gọi parser qua FastAPI `TestClient` và dọn object sau khi hoàn thành.

Đây là integration test của Python parser với MinIO, không phải test Java-to-Python.

## Smoke test job pipeline

### 1. Trigger mock crawler

```bash
curl -s -X POST \
  http://localhost:8080/api/admin/crawlers/mock/run \
  | jq
```

Ở database mới:

```json
{
  "sourceCode": "MOCK",
  "insertedCount": 2,
  "totalRawJobs": 2
}
```

Mock site hiện có hai job.

### 2. Kiểm tra `raw_jobs`

Qua API:

```bash
curl -s \
  "http://localhost:8080/api/raw-jobs?limit=20" \
  | jq
```

Qua MongoDB:

```bash
docker compose --env-file .env exec mongo \
  mongosh \
  -u root \
  -p password \
  --authenticationDatabase admin \
  autojob \
  --quiet \
  --eval '
    db.raw_jobs.find(
      {sourceCode: "MOCK"},
      {
        title: 1,
        fingerprint: 1,
        firstSeenAt: 1,
        lastSeenAt: 1,
        collectedAt: 1
      }
    ).sort({collectedAt: -1}).forEach(printjson)
  '
```

Cần thấy:

* Hai document nguồn `MOCK`.
* `fingerprint` có giá trị.
* `detailUrl` và `applyUrl` có giá trị.
* `firstSeenAt`, `lastSeenAt` và `collectedAt` đã được ghi.

### 3. Kiểm tra `normalized_jobs`

```bash
curl -s \
  "http://localhost:8080/api/normalized-jobs?page=0&size=20&sourceCode=MOCK" \
  | jq
```

MongoDB:

```bash
docker compose --env-file .env exec mongo \
  mongosh \
  -u root \
  -p password \
  --authenticationDatabase admin \
  autojob \
  --quiet \
  --eval '
    db.normalized_jobs.find(
      {sourceCode: "MOCK"},
      {
        rawJobId: 1,
        title: 1,
        skills: 1,
        locations: 1,
        seniority: 1,
        normalizationVersion: 1,
        rawContentHash: 1
      }
    ).forEach(printjson)
  '
```

Cần kiểm tra:

* Mỗi normalized job có `rawJobId`.
* `normalizationVersion` là `rule-v1`, trừ khi đã override.
* `rawContentHash` có giá trị.
* `embeddingText` tồn tại trong MongoDB dù API detail không expose field này.

Kiểm tra riêng `embeddingText`:

```bash
docker compose --env-file .env exec mongo \
  mongosh \
  -u root \
  -p password \
  --authenticationDatabase admin \
  autojob \
  --quiet \
  --eval '
    db.normalized_jobs.find(
      {sourceCode: "MOCK"},
      {title: 1, embeddingText: 1}
    ).forEach(printjson)
  '
```

### 4. Kiểm tra `job_embeddings`

```bash
NORMALIZED_JOB_ID=$(
  curl -s \
    "http://localhost:8080/api/normalized-jobs?page=0&size=1&sourceCode=MOCK" \
  | jq -r '.content[0].id'
)

curl -s \
  "http://localhost:8080/api/job-embeddings/${NORMALIZED_JOB_ID}" \
  | jq
```

Kết quả thành công:

```text
status = READY
dimension = 384
normalized = true
qdrantCollection = job_vectors_v1
qdrantPointId có giá trị
lastError = null
```

Kiểm tra toàn bộ record:

```bash
docker compose --env-file .env exec mongo \
  mongosh \
  -u root \
  -p password \
  --authenticationDatabase admin \
  autojob \
  --quiet \
  --eval '
    db.job_embeddings.find(
      {},
      {
        normalizedJobId: 1,
        embeddingVersion: 1,
        dimension: 1,
        status: 1,
        qdrantPointId: 1,
        lastError: 1
      }
    ).forEach(printjson)
  '
```

Nếu embedding hoặc Qdrant lỗi, record có thể ở trạng thái `FAILED` trong khi normalized job vẫn tồn tại.

### 5. Kiểm tra Qdrant collection

```bash
curl -s \
  http://localhost:6333/collections/job_vectors_v1 \
  | jq
```

Các giá trị cần kiểm tra:

```text
vector size = 384
distance = Cosine
points_count > 0
```

Đọc point và payload:

```bash
curl -s -X POST \
  http://localhost:6333/collections/job_vectors_v1/points/scroll \
  -H "Content-Type: application/json" \
  -d '{
    "limit": 10,
    "with_payload": true,
    "with_vector": false
  }' \
  | jq
```

Payload cần có:

```text
jobId
sourceCode
normalizationVersion
embeddingVersion
textHash
```

`jobId` phải khớp với `_id` của `normalized_jobs`.

## Kiểm tra idempotency

Chạy lại mock crawler:

```bash
curl -s -X POST \
  http://localhost:8080/api/admin/crawlers/mock/run \
  | jq
```

Kết quả dự kiến:

```json
{
  "sourceCode": "MOCK",
  "insertedCount": 0,
  "totalRawJobs": 2
}
```

Kiểm tra:

* Số document `raw_jobs` không tăng.
* `lastSeenAt` hoặc `collectedAt` được cập nhật.
* Normalizer trả logic `UNCHANGED`.
* Không tạo thêm `normalized_jobs` cùng version.
* Không tạo thêm Qdrant point cùng job và embedding version.

## Rebuild embedding sau lỗi

Sau khi embedding service và Qdrant đã hoạt động:

```bash
curl -s -X POST \
  "http://localhost:8080/api/admin/job-embeddings/${NORMALIZED_JOB_ID}/rebuild?force=true" \
  | jq
```

Sau đó kiểm tra lại MongoDB và Qdrant.

## Upload và kiểm tra CV

### 1. Upload file thật

Sử dụng một file PDF, DOC hoặc DOCX hợp lệ:

```bash
CV_RESPONSE=$(
  curl -s -X POST \
    http://localhost:8080/api/cvs \
    -F "file=@./sample-cv.pdf"
)

echo "$CV_RESPONSE" | jq

RAW_CV_ID=$(
  echo "$CV_RESPONSE" |
  jq -r '.id'
)
```

Kết quả cần có:

```text
status = UPLOADED
sha256 có giá trị
sizeBytes > 0
```

Các file trong `scripts/` hiện là placeholder rỗng, vì vậy repository chưa cung cấp command tạo smoke CV sẵn.

### 2. Kiểm tra `raw_cvs`

```bash
docker compose --env-file .env exec mongo \
  mongosh \
  -u root \
  -p password \
  --authenticationDatabase admin \
  autojob \
  --quiet \
  --eval '
    db.raw_cvs.findOne(
      {_id: "'"${RAW_CV_ID}"'"},
      {
        ownerUserId: 1,
        bucket: 1,
        objectKey: 1,
        originalFilename: 1,
        extension: 1,
        contentType: 1,
        sizeBytes: 1,
        sha256: 1,
        status: 1,
        uploadedAt: 1
      }
    )
  '
```

Cần thấy:

```text
bucket = autojob-cvs
objectKey bắt đầu bằng raw/
status = UPLOADED
```

### 3. Kiểm tra MinIO object

Liệt kê bucket bằng MinIO client:

```bash
docker compose --env-file .env run --rm \
  --entrypoint /bin/sh \
  minio-init \
  -c '
    mc alias set local \
      http://minio:9000 \
      "$MINIO_ROOT_USER" \
      "$MINIO_ROOT_PASSWORD" &&
    mc ls --recursive \
      "local/$MINIO_BUCKET_CVS"
  '
```

Object key trong MinIO phải trùng `raw_cvs.objectKey`.

Có thể kiểm tra trực tiếp tại:

```text
http://localhost:9001
```

### 4. Giới hạn verification hiện tại

Sau upload, không nên kỳ vọng:

```text
raw_cvs.status = PARSED
candidate_profiles có document
candidate_embeddings có document
match_results có document
```

Các bước này chưa được Java backend tích hợp.

## Checklist smoke test

| Kiểm tra             | Kết quả mong đợi               |
| -------------------- | ------------------------------ |
| Backend health       | `UP`                           |
| Embedding readiness  | `ready`                        |
| Mock crawler lần đầu | Hai job được insert            |
| `raw_jobs`           | Có hai job `MOCK`              |
| `normalized_jobs`    | Có normalized document         |
| `job_embeddings`     | `READY`                        |
| Qdrant               | Collection 384 chiều, có point |
| Mock crawler lần hai | `insertedCount = 0`            |
| CV upload            | `status = UPLOADED`            |
| MinIO                | Có CV object                   |
| Candidate profile    | Chưa có                        |
| Matching result      | Chưa có                        |
