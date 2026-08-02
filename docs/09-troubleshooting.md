# Troubleshooting

## Docker service chưa chạy

### Triệu chứng

```text
Connection refused
No route to host
Backend không kết nối được MongoDB, Qdrant, MinIO hoặc embedding service
```

### Nguyên nhân thường gặp

* Docker Compose chưa được khởi động.
* Container đã exit hoặc health check thất bại.
* Port local đang bị process khác sử dụng.
* Image build chưa hoàn thành.

### Cách xử lý

```bash
docker compose --env-file .env ps
docker compose --env-file .env logs --tail=200
```

Khởi động lại:

```bash
docker compose --env-file .env up -d --build
```

Kiểm tra từng dependency:

```bash
curl -f http://localhost:8002/ready
curl -f http://localhost:9000/minio/health/live
curl -f http://localhost:6333/collections
curl -f http://localhost:18080/jobs.html
```

## MongoDB authentication lỗi

### Triệu chứng

Backend không khởi động hoặc log có thông báo tương tự:

```text
Authentication failed
MongoSecurityException
Command failed with error 13
```

### Nguyên nhân thường gặp

* Username hoặc password trong Spring Boot không khớp Docker Compose.
* Dùng sai authentication database.
* Đã đổi `.env` nhưng backend vẫn dùng giá trị trong `application-local.yml`.
* MongoDB volume được tạo bằng credentials cũ.

### Cách xử lý

Kiểm tra trực tiếp:

```bash
docker compose --env-file .env exec mongo \
  mongosh \
  -u root \
  -p password \
  --authenticationDatabase admin \
  autojob \
  --eval 'db.runCommand({ping: 1})'
```

Profile local mặc định dùng:

```text
username = root
password = password
authentication database = admin
port = 27018
```

Khi đổi credentials, truyền chúng cho Java process:

```bash
SPRING_DATA_MONGODB_USERNAME=root \
SPRING_DATA_MONGODB_PASSWORD=password \
SPRING_DATA_MONGODB_AUTHENTICATION_DATABASE=admin \
bash ./mvnw \
  -pl autojob-app \
  -am \
  spring-boot:run \
  -Dspring-boot.run.profiles=local
```

Nếu chỉ là dữ liệu local và volume đang giữ credentials cũ:

```bash
docker compose --env-file .env down -v
docker compose --env-file .env up -d
```

Lệnh này xóa toàn bộ dữ liệu local.

## MinIO bucket chưa có

### Triệu chứng

* CV upload thất bại.
* CV parser `/ready` trả `DOWN`.
* Log có lỗi bucket không tồn tại hoặc không truy cập được.

### Nguyên nhân thường gặp

* `minio-init` chưa chạy thành công.
* Bucket name giữa Java, Python và Docker Compose không khớp.
* Credentials MinIO không khớp.
* MinIO chưa ready khi client kết nối.

### Cách xử lý

```bash
docker compose --env-file .env \
  up -d minio

docker compose --env-file .env \
  up minio-init
```

Xem log:

```bash
docker compose --env-file .env \
  logs minio minio-init
```

Bucket mặc định:

```text
autojob-cvs
```

Kiểm tra bằng MinIO client:

```bash
docker compose --env-file .env run --rm \
  --entrypoint /bin/sh \
  minio-init \
  -c '
    mc alias set local \
      http://minio:9000 \
      "$MINIO_ROOT_USER" \
      "$MINIO_ROOT_PASSWORD" &&
    mc ls local
  '
```

## Maven Wrapper lỗi

### Triệu chứng

```text
Permission denied: ./mvnw
Failed to fetch apache-maven-3.9.9-bin.zip
Could not resolve dependencies
```

### Nguyên nhân thường gặp

* File ZIP không giữ executable permission.
* Máy không truy cập được Maven Central.
* Proxy hoặc DNS chặn download.
* Maven cache đang có file tải dở.

### Cách xử lý

Chạy wrapper qua Bash:

```bash
cd backend
bash ./mvnw test
```

Wrapper cần tải Maven từ:

```text
repo.maven.apache.org
```

Nếu không có Internet, dùng Maven đã cài sẵn:

```bash
mvn --version
mvn test
```

Phiên bản phù hợp nhất với repository là Maven 3.9.9, đúng theo wrapper configuration.

Nếu cache Maven Wrapper bị hỏng, xóa bản tải lỗi trong:

```text
~/.m2/wrapper/
```

rồi chạy lại khi có kết nối mạng.

## Embedding service chưa ready

### Triệu chứng

```bash
curl http://localhost:8002/ready
```

trả `503`, hoặc container ở trạng thái `unhealthy`.

`job_embeddings` có thể chuyển thành:

```text
FAILED
```

### Nguyên nhân thường gặp

* Model thật đang được tải lần đầu.
* Không có kết nối tới Hugging Face model repository.
* Không đủ RAM.
* Model revision không tải được.
* Provider load bị lỗi.

### Cách xử lý

Xem log:

```bash
docker compose --env-file .env \
  logs -f embedding-service
```

Health process:

```bash
curl -s http://localhost:8002/health | jq
```

Readiness:

```bash
curl -s http://localhost:8002/ready | jq
```

Để smoke test nhanh, dùng fake provider trong `.env`:

```dotenv
EMBEDDING_PROVIDER=fake
EMBEDDING_EXPECTED_VERSION=autojob/fake-sha256@deterministic-v1|prep-v1|l2
```

Recreate service:

```bash
docker compose --env-file .env \
  up -d --build --force-recreate embedding-service
```

Java backend cũng phải được khởi động với cùng expected version.

## Embedding version mismatch

### Triệu chứng

`job_embeddings.lastError` hoặc backend log có nội dung:

```text
embeddingVersion mismatch
```

### Nguyên nhân thường gặp

* Python service dùng fake provider nhưng Java vẫn kỳ vọng model thật.
* Model name, revision, preprocessing version hoặc normalization strategy khác nhau.
* Chỉ sửa `.env` của Docker Compose nhưng không truyền biến cho Java process.
* Service cũ chưa được recreate.

### Cách xử lý

Kiểm tra version Python đang trả:

```bash
curl -s http://localhost:8002/ready \
  | jq '.embeddingVersion'
```

So sánh với Java configuration:

```text
EMBEDDING_EXPECTED_VERSION
```

Với provider thật mặc định:

```text
intfloat/multilingual-e5-small@c007d7ef6fd86656326059b28395a7a03a7c5846|prep-v1|l2
```

Với fake provider:

```text
autojob/fake-sha256@deterministic-v1|prep-v1|l2
```

Khởi động backend với version đúng:

```bash
EMBEDDING_EXPECTED_VERSION='autojob/fake-sha256@deterministic-v1|prep-v1|l2' \
bash ./mvnw \
  -pl autojob-app \
  -am \
  spring-boot:run \
  -Dspring-boot.run.profiles=local
```

Sau đó force rebuild embedding bị lỗi.

## Qdrant dimension mismatch

### Triệu chứng

Log hoặc `job_embeddings.lastError` có nội dung:

```text
Qdrant collection dimension mismatch
Qdrant vector dimension mismatch
Embedding and Qdrant dimensions do not match
```

### Nguyên nhân thường gặp

* Qdrant collection đã được tạo với dimension khác.
* `EMBEDDING_EXPECTED_DIMENSION` và `QDRANT_VECTOR_DIMENSION` không khớp.
* Đã đổi model nhưng vẫn dùng collection cũ.
* Python service trả vector có dimension khác 384.

### Cách xử lý

Kiểm tra embedding service:

```bash
curl -s http://localhost:8002/ready | jq
```

Kiểm tra collection:

```bash
curl -s \
  http://localhost:6333/collections/job_vectors_v1 \
  | jq
```

Các giá trị mặc định phải cùng là:

```text
384
```

Để xóa collection local và tạo lại:

```bash
curl -X DELETE \
  http://localhost:6333/collections/job_vectors_v1
```

Sau đó force rebuild một job embedding. Java sẽ tạo lại collection.

Một lựa chọn khác là dùng collection version mới:

```dotenv
QDRANT_JOB_COLLECTION=job_vectors_v2
```

Không trộn vector từ các model hoặc dimension khác nhau trong cùng collection.

## CV parser thiếu `antiword`

### Triệu chứng

CV parser `/ready` trả:

```json
{
  "status": "DOWN",
  "docExtractor": "DOWN",
  "details": [
    "antiword is unavailable"
  ]
}
```

File DOC không parse được.

### Nguyên nhân thường gặp

* Chạy Python service trực tiếp ngoài Docker.
* `antiword` chưa được cài hoặc không có trong `PATH`.
* Docker image parser chưa được build từ Dockerfile hiện tại.

### Cách xử lý

Kiểm tra:

```bash
command -v antiword
```

Trên Debian hoặc Ubuntu:

```bash
sudo apt-get update
sudo apt-get install antiword
```

Dockerfile của `cv-parser-service` đã cài `antiword`. Build lại:

```bash
docker build \
  -t autojob-cv-parser:local \
  ai-services/cv-parser-service
```

Lưu ý: service này hiện chưa nằm trong Docker Compose và chưa được Java backend gọi.

## Upload file sai định dạng

### Triệu chứng

API trả một trong các error code:

```text
CV_FILE_EMPTY
CV_FILENAME_MISSING
CV_FILE_TYPE_NOT_ALLOWED
CV_FILE_SIGNATURE_INVALID
CV_FILE_TOO_LARGE
```

### Nguyên nhân thường gặp

* Upload file không phải PDF, DOC hoặc DOCX.
* Chỉ đổi extension của file.
* PDF không bắt đầu bằng `%PDF-`.
* DOC không có OLE signature.
* DOCX không có cấu trúc Office ZIP hợp lệ.
* File vượt quá 10 MB.

### Cách xử lý

Dùng file thật:

```bash
file ./sample-cv.pdf
ls -lh ./sample-cv.pdf
```

Upload:

```bash
curl -s -X POST \
  http://localhost:8080/api/cvs \
  -F "file=@./sample-cv.pdf" \
  | jq
```

Không dùng text file được đổi tên thành `.pdf`.

Khi đổi giới hạn, phải đồng bộ:

```text
CV_MAX_FILE_SIZE_MB
CV_MAX_FILE_SIZE
CV_MAX_REQUEST_SIZE
```

## Admin API trả `403`

### Triệu chứng

```json
{
  "status": 403,
  "error": "FORBIDDEN",
  "message": "You do not have permission to access this resource"
}
```

### Nguyên nhân thường gặp

* `AUTH_PUBLIC_API_MODE=false`.
* JWT chỉ có role `USER`.
* Register mặc định không tạo admin.
* Role đã được sửa trong MongoDB nhưng client vẫn dùng access token cũ.

### Cách xử lý

Cho local smoke test, giữ:

```text
AUTH_PUBLIC_API_MODE=true
```

Nếu muốn kiểm tra authorization thật, thêm role admin trong MongoDB:

```javascript
db.users.updateOne(
  {emailNormalized: "developer@example.com"},
  {$addToSet: {roles: "ADMIN"}}
)
```

Sau đó login lại để nhận JWT mới chứa role `ADMIN`.

Gọi API:

```bash
curl -s -X POST \
  http://localhost:8080/api/admin/crawlers/mock/run \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  | jq
```

Nếu không gửi token hoặc token không hợp lệ, response là `401`, không phải `403`.

## Mock crawler không insert thêm dữ liệu

### Triệu chứng

Lần chạy sau trả:

```json
{
  "insertedCount": 0
}
```

### Nguyên nhân thường gặp

Fingerprint của hai mock job đã tồn tại.

Đây là hành vi idempotent dự kiến, không phải lỗi.

Crawler vẫn có thể:

* Update `lastSeenAt`.
* Update `collectedAt`.
* Publish `JobRawCollectedEvent`.

Normalizer có thể kết luận nội dung là `UNCHANGED`, nên embedding không chạy lại.

### Cách xử lý

Kiểm tra fingerprint:

```bash
curl -s \
  "http://localhost:8080/api/raw-jobs?limit=20" \
  | jq '.[].fingerprint'
```

Muốn chạy lại normalization:

```bash
curl -s -X POST \
  "http://localhost:8080/api/raw-jobs/${RAW_JOB_ID}/normalize?force=true" \
  | jq
```

Muốn chạy lại embedding:

```bash
curl -s -X POST \
  "http://localhost:8080/api/admin/job-embeddings/${NORMALIZED_JOB_ID}/rebuild?force=true" \
  | jq
```

Muốn lặp lại smoke test từ database sạch:

```bash
docker compose --env-file .env down -v
docker compose --env-file .env up -d --build
```

Lệnh này xóa toàn bộ MongoDB, Qdrant, MinIO và model cache local.
