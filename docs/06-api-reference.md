# API reference

## Tổng quan

Base URL khi chạy local:

```text
http://localhost:8080
```

Các API trả và nhận JSON, ngoại trừ CV upload sử dụng `multipart/form-data`.

Cấu hình local mặc định:

```text
AUTH_PUBLIC_API_MODE=true
```

Khi đó tất cả API đều có thể gọi không cần token. Nếu request gửi JWT hợp lệ, backend vẫn tạo authentication principal.

Khi chuyển sang:

```text
AUTH_PUBLIC_API_MODE=false
```

quyền truy cập hiện tại là:

| Nhóm API                                    | Quyền                |
| ------------------------------------------- | -------------------- |
| `/api/auth/**`                              | Public               |
| `/api/admin/**`                             | Yêu cầu role `ADMIN` |
| `/api/cvs/**`                               | Yêu cầu đăng nhập    |
| Raw jobs, normalized jobs, embeddings query | Public               |
| Actuator health và OpenAPI                  | Public               |

Gửi access token:

```bash
-H "Authorization: Bearer ${ACCESS_TOKEN}"
```

## Authentication

### Đăng ký tài khoản

```text
POST /api/auth/register
```

Mục đích:

* Tạo user mới.
* Trả access token và refresh token.
* Email được normalize và phải là duy nhất.

Request:

```json
{
  "email": "developer@example.com",
  "password": "change-me-123",
  "displayName": "AutoJob Developer"
}
```

Ví dụ:

```bash
curl -s -X POST \
  http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "developer@example.com",
    "password": "change-me-123",
    "displayName": "AutoJob Developer"
  }' \
  | jq
```

Response: `201 Created`

```json
{
  "tokenType": "Bearer",
  "accessToken": "...",
  "accessTokenExpiresAt": "...",
  "refreshToken": "...",
  "refreshTokenExpiresAt": "...",
  "user": {
    "id": "...",
    "email": "developer@example.com",
    "displayName": "AutoJob Developer",
    "roles": [
      "USER"
    ],
    "createdAt": "..."
  }
}
```

### Đăng nhập

```text
POST /api/auth/login
```

Request:

```json
{
  "email": "developer@example.com",
  "password": "change-me-123"
}
```

Ví dụ:

```bash
curl -s -X POST \
  http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "developer@example.com",
    "password": "change-me-123"
  }' \
  | jq
```

Response chính giống API đăng ký: access token, refresh token và thông tin user.

### Làm mới token

```text
POST /api/auth/refresh
```

Request:

```json
{
  "refreshToken": "<refresh-token>"
}
```

Ví dụ:

```bash
curl -s -X POST \
  http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "'"${REFRESH_TOKEN}"'"
  }' \
  | jq
```

Backend rotate refresh token. Response chứa cặp access token và refresh token mới.

Refresh token cũ không nên tiếp tục được sử dụng.

### Đăng xuất

```text
POST /api/auth/logout
```

Request:

```json
{
  "refreshToken": "<refresh-token>"
}
```

Ví dụ:

```bash
curl -i -X POST \
  http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "'"${REFRESH_TOKEN}"'"
  }'
```

Response:

```text
204 No Content
```

### Kiểm tra principal hiện tại

```text
GET /api/auth/me
```

Ví dụ có token:

```bash
curl -s \
  http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  | jq
```

Response:

```json
{
  "authenticated": true,
  "userId": "...",
  "email": "developer@example.com",
  "roles": [
    "USER"
  ]
}
```

Không gửi JWT:

```json
{
  "authenticated": false,
  "userId": null,
  "email": null,
  "roles": null
}
```

## CV upload

### Upload CV

```text
POST /api/cvs
Content-Type: multipart/form-data
```

Mục đích:

* Validate PDF, DOC hoặc DOCX.
* Tính SHA-256.
* Lưu file vào MinIO.
* Lưu metadata vào `raw_cvs`.

Ví dụ:

```bash
curl -s -X POST \
  http://localhost:8080/api/cvs \
  -F "file=@./sample-cv.pdf" \
  | jq
```

Response: `201 Created`

```json
{
  "id": "<rawCvId>",
  "ownerUserId": null,
  "originalFilename": "sample-cv.pdf",
  "extension": "pdf",
  "contentType": "application/pdf",
  "sizeBytes": 185320,
  "sha256": "...",
  "status": "UPLOADED",
  "uploadedAt": "..."
}
```

Nếu request có JWT hợp lệ, `ownerUserId` được lấy từ subject của token.

API hiện chỉ upload và lưu metadata. Nó chưa gọi `cv-parser-service`.

### Đọc metadata CV

```text
GET /api/cvs/{rawCvId}
```

Ví dụ:

```bash
RAW_CV_ID="<raw-cv-id>"

curl -s \
  "http://localhost:8080/api/cvs/${RAW_CV_ID}" \
  | jq
```

Response có cùng cấu trúc với upload response.

API không trả:

* MinIO bucket.
* MinIO object key.
* Upload IP.
* `lastError`.

Source code hiện chưa kiểm tra ownership khi đọc theo ID.

## Raw jobs

### Liệt kê raw jobs

```text
GET /api/raw-jobs
```

Query:

| Parameter | Mặc định | Ghi chú                           |
| --------- | -------: | --------------------------------- |
| `limit`   |     `20` | Backend giới hạn từ `1` đến `100` |

Ví dụ:

```bash
curl -s \
  "http://localhost:8080/api/raw-jobs?limit=20" \
  | jq
```

Response là JSON array, sắp xếp theo `collectedAt` giảm dần:

```json
[
  {
    "id": "...",
    "sourceCode": "MOCK",
    "sourceJobId": "mock-java-backend",
    "fingerprint": "MOCK:mock-java-backend",
    "title": "Java Backend Developer",
    "companyName": "AutoJob Labs",
    "salaryText": "...",
    "locationText": "...",
    "experienceText": "...",
    "skills": [
      "Java",
      "Spring Boot"
    ],
    "detailUrl": "http://localhost:18080/jobs/java-backend.html",
    "applyUrl": "http://localhost:18080/jobs/java-backend.html",
    "applyType": "DETAIL_PAGE",
    "firstSeenAt": "...",
    "lastSeenAt": "...",
    "collectedAt": "...",
    "expiresAt": "...",
    "rawPayloadPurgedAt": "..."
  }
]
```

Hiện chưa có API:

```text
GET /api/raw-jobs/{id}
```

### Normalize một raw job

```text
POST /api/raw-jobs/{rawJobId}/normalize
```

Query:

| Parameter | Mặc định | Ý nghĩa                                             |
| --------- | -------- | --------------------------------------------------- |
| `force`   | `false`  | Bỏ qua kiểm tra unchanged và chạy lại normalization |

Ví dụ:

```bash
RAW_JOB_ID="<raw-job-id>"

curl -s -X POST \
  "http://localhost:8080/api/raw-jobs/${RAW_JOB_ID}/normalize" \
  | jq
```

Force normalize:

```bash
curl -s -X POST \
  "http://localhost:8080/api/raw-jobs/${RAW_JOB_ID}/normalize?force=true" \
  | jq
```

Response là normalized job detail.

Nếu normalized content thay đổi hoặc dùng `force=true`, service publish event để chạy job embedding.

## Normalized jobs

### Liệt kê normalized jobs

```text
GET /api/normalized-jobs
```

Query:

| Parameter              | Mặc định | Ghi chú                        |
| ---------------------- | -------: | ------------------------------ |
| `page`                 |      `0` | Bắt đầu từ `0`                 |
| `size`                 |     `20` | Từ `1` đến `100`               |
| `sourceCode`           | Không có | Được normalize thành uppercase |
| `normalizationVersion` | Không có | Ví dụ `rule-v1`                |

Ví dụ:

```bash
curl -s \
  "http://localhost:8080/api/normalized-jobs?page=0&size=20&sourceCode=MOCK" \
  | jq
```

Response:

```json
{
  "content": [
    {
      "id": "...",
      "rawJobId": "...",
      "sourceCode": "MOCK",
      "sourceJobId": "...",
      "title": "Java Backend Developer",
      "companyName": "AutoJob Labs",
      "skills": [
        "Java",
        "Spring Boot"
      ],
      "locations": [
        "Ho Chi Minh City"
      ],
      "salaryText": "...",
      "salaryMin": null,
      "salaryMax": null,
      "currency": null,
      "experienceMin": 2.0,
      "experienceMax": null,
      "seniority": "MIDDLE",
      "jobType": "FULL_TIME",
      "normalizationVersion": "rule-v1",
      "postedAt": null,
      "deadlineAt": null,
      "normalizedAt": "..."
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Summary response không trả description, requirements, benefits hoặc `embeddingText`.

### Đọc normalized job

```text
GET /api/normalized-jobs/{id}
```

Ví dụ:

```bash
NORMALIZED_JOB_ID="<normalized-job-id>"

curl -s \
  "http://localhost:8080/api/normalized-jobs/${NORMALIZED_JOB_ID}" \
  | jq
```

Response bổ sung:

* `sourceFingerprint`.
* `rawContentHash`.
* `descriptionText`.
* `requirementsText`.
* `benefitsText`.
* `detailUrl`.
* `applyUrl`.
* `applyType`.

API hiện không trả `embeddingText`.

### Re-normalize theo batch

```text
POST /api/admin/job-normalization/renormalize
```

Request body có thể bỏ trống. Giá trị mặc định:

```text
page = 0
size = 100
force = false
```

Request ví dụ:

```bash
curl -s -X POST \
  http://localhost:8080/api/admin/job-normalization/renormalize \
  -H "Content-Type: application/json" \
  -d '{
    "sourceCode": "MOCK",
    "page": 0,
    "size": 100,
    "force": false
  }' \
  | jq
```

`size` tối đa là `500`.

Response chính:

```json
{
  "sourceCode": "MOCK",
  "normalizationVersion": "rule-v1",
  "force": false,
  "page": 0,
  "size": 100,
  "processed": 2,
  "totalRawJobs": 2,
  "totalPages": 1,
  "hasNext": false,
  "nextPage": null,
  "created": 0,
  "updated": 0,
  "unchanged": 2,
  "failed": 0,
  "rawPayloadPurged": 2,
  "purgeFailed": 0,
  "failures": []
}
```

Batch service tiếp tục xử lý các item còn lại khi một raw job bị lỗi và ghi lỗi vào `failures`.

## Job embeddings

### Đọc embedding metadata mới nhất

```text
GET /api/job-embeddings/{normalizedJobId}
```

Ví dụ:

```bash
curl -s \
  "http://localhost:8080/api/job-embeddings/${NORMALIZED_JOB_ID}" \
  | jq
```

Response:

```json
{
  "normalizedJobId": "...",
  "normalizationVersion": "rule-v1",
  "modelName": "intfloat/multilingual-e5-small",
  "modelRevision": "...",
  "embeddingVersion": "...|prep-v1|l2",
  "textHash": "...",
  "dimension": 384,
  "normalized": true,
  "status": "READY",
  "qdrantCollection": "job_vectors_v1",
  "qdrantPointId": "...",
  "embeddedAt": "...",
  "lastError": null
}
```

API trả metadata, không trả vector.

### Rebuild job embedding

```text
POST /api/admin/job-embeddings/{normalizedJobId}/rebuild
```

Query:

| Parameter | Mặc định | Ý nghĩa                                                                      |
| --------- | -------- | ---------------------------------------------------------------------------- |
| `force`   | `false`  | Gọi lại embedding và upsert lại Qdrant dù record hiện tại có thể tái sử dụng |

Ví dụ:

```bash
curl -s -X POST \
  "http://localhost:8080/api/admin/job-embeddings/${NORMALIZED_JOB_ID}/rebuild?force=true" \
  | jq
```

Response có cùng cấu trúc với API đọc embedding.

## Crawler

### Trigger mock crawler

```text
POST /api/admin/crawlers/mock/run
```

Ví dụ:

```bash
curl -s -X POST \
  http://localhost:8080/api/admin/crawlers/mock/run \
  | jq
```

Response:

```json
{
  "sourceCode": "MOCK",
  "insertedCount": 2,
  "totalRawJobs": 2
}
```

`insertedCount` là chênh lệch tổng số `raw_jobs` trước và sau khi chạy.

Khi fingerprint đã tồn tại:

```json
{
  "sourceCode": "MOCK",
  "insertedCount": 0,
  "totalRawJobs": 2
}
```

Crawler vẫn update raw job và publish event, nhưng normalizer có thể kết luận nội dung là `UNCHANGED`.

Hiện chưa có live crawler API cho:

```text
ITVIEC
JOBOKO
TOPDEV
VIECLAM24H
```

## Source Discovery

Source Discovery hiện chỉ sinh candidate URL theo common path. Nó chưa request hoặc xác minh các URL này.

### Tạo website source

```text
POST /api/admin/source-discovery/website-sources
```

Request:

```json
{
  "sourceCode": "EXAMPLE",
  "domain": "https://example.com/"
}
```

Ví dụ:

```bash
curl -s -X POST \
  http://localhost:8080/api/admin/source-discovery/website-sources \
  -H "Content-Type: application/json" \
  -d '{
    "sourceCode": "EXAMPLE",
    "domain": "https://example.com/"
  }' \
  | jq
```

Response:

```json
{
  "id": "...",
  "sourceCode": "EXAMPLE",
  "domain": "example.com",
  "status": "PENDING_DISCOVERY",
  "createdAt": "...",
  "updatedAt": "..."
}
```

### Chạy discovery

```text
POST /api/admin/source-discovery/website-sources/{id}/run
```

Ví dụ:

```bash
WEBSITE_SOURCE_ID="<website-source-id>"

curl -s -X POST \
  "http://localhost:8080/api/admin/source-discovery/website-sources/${WEBSITE_SOURCE_ID}/run" \
  | jq
```

Response hiện tạo bốn candidate:

```text
https://example.com/careers
https://example.com/jobs
https://example.com/tuyen-dung
https://example.com/viec-lam
```

Mỗi result có dạng:

```json
{
  "id": "...",
  "websiteSourceId": "...",
  "sourceCode": "EXAMPLE",
  "candidateUrl": "https://example.com/careers",
  "detectionType": "COMMON_PATH",
  "status": "PENDING_REVIEW",
  "discoveredAt": "..."
}
```

### Đọc discovery results

```text
GET /api/admin/source-discovery/website-sources/{id}/results
```

Ví dụ:

```bash
curl -s \
  "http://localhost:8080/api/admin/source-discovery/website-sources/${WEBSITE_SOURCE_ID}/results" \
  | jq
```

Hiện chưa có API approve hoặc reject result.

## Parser fixture

Các API này đọc file HTML từ filesystem mà Spring Boot process có quyền truy cập.

Chúng dùng để kiểm tra parser, không fetch website thật.

### Parse list fixture

```text
POST /api/parsers/{sourceCode}/list-file
```

Request:

```json
{
  "filePath": "/absolute/path/to/list_page_1.html",
  "baseUrl": "https://itviec.com"
}
```

Ví dụ từ thư mục gốc repository:

```bash
FIXTURE_PATH="$PWD/backend/modules/job-crawler/src/test/resources/fixtures/itviec/list_page_1.html"

curl -s -X POST \
  http://localhost:8080/api/parsers/ITVIEC/list-file \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "'"${FIXTURE_PATH}"'",
    "baseUrl": "https://itviec.com"
  }' \
  | jq
```

Response:

```json
{
  "sourceCode": "ITVIEC",
  "detailUrlCount": 2,
  "detailUrls": [
    "https://itviec.com/..."
  ]
}
```

API này chỉ parse danh sách URL, không lưu MongoDB.

### Parse detail fixture

```text
POST /api/parsers/{sourceCode}/detail-file
```

Request:

```json
{
  "detailUrl": "https://itviec.com/it-jobs/example",
  "filePath": "/absolute/path/to/detail_page_1.html",
  "listUrl": "https://itviec.com/it-jobs",
  "rawRetentionDays": 30
}
```

Ví dụ:

```bash
FIXTURE_PATH="$PWD/backend/modules/job-crawler/src/test/resources/fixtures/itviec/detail_page_1.html"

curl -s -X POST \
  http://localhost:8080/api/parsers/ITVIEC/detail-file \
  -H "Content-Type: application/json" \
  -d '{
    "detailUrl": "https://itviec.com/it-jobs/example",
    "filePath": "'"${FIXTURE_PATH}"'",
    "listUrl": "https://itviec.com/it-jobs",
    "rawRetentionDays": 30
  }' \
  | jq
```

API này:

1. Parse detail fixture.
2. Lưu hoặc update `raw_jobs`.
3. Publish `JobRawCollectedEvent`.
4. Trigger normalization.
5. Có thể trigger job embedding và Qdrant upsert.

Response chính:

```json
{
  "id": "...",
  "sourceCode": "ITVIEC",
  "sourceJobId": "...",
  "fingerprint": "...",
  "title": "...",
  "companyName": "...",
  "salaryText": "...",
  "locationText": "...",
  "experienceText": "...",
  "seniorityText": "...",
  "jobTypeText": "...",
  "deadlineText": "...",
  "postedText": "...",
  "skills": [],
  "detailUrl": "...",
  "applyUrl": "...",
  "applyType": "DETAIL_PAGE",
  "rawHtmlStored": false,
  "rawTextStored": false
}
```

## API đề xuất

Các API sau chưa tồn tại trong source code:

```text
POST /api/cvs/{rawCvId}/parse
GET  /api/candidate-profiles/{candidateProfileId}
POST /api/candidate-profiles/{candidateProfileId}/embeddings/rebuild
POST /api/candidate-profiles/{candidateProfileId}/matches
GET  /api/candidate-profiles/{candidateProfileId}/matches
```

Chỉ nên bổ sung sau khi Java backend đã được nối với CV parser, candidate persistence và Matching Engine.
