# API Reference

Base local:

```text
http://localhost:8080
```

---

## Auth

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```

Local mặc định:

```dotenv
AUTH_PUBLIC_API_MODE=true
```

---

# CV API

## Upload

```text
POST /api/cvs
Content-Type: multipart/form-data
```

Field:

```text
file
```

Response:

```json
{
  "id": "...",
  "ownerUserId": "public-local-user",
  "originalFilename": "cv.pdf",
  "extension": "pdf",
  "contentType": "application/pdf",
  "sizeBytes": 123456,
  "sha256": "...",
  "status": "UPLOADED",
  "uploadedAt": "..."
}
```

---

## Raw CV metadata

```text
GET /api/cvs/{rawCvId}
```

---

## Parse CV

```text
POST /api/cvs/{rawCvId}/parse
```

Response là candidate profile.

Parse:

```text
Java
→ cv-parser-service
→ candidate_profiles
→ candidate embedding trigger
```

---

## Read candidate profile

```text
GET /api/cvs/{rawCvId}/profile
```

---

# Candidate Embedding API

## Latest

```text
GET /api/admin/candidate-embeddings/{candidateProfileId}
```

---

## Rebuild

```text
POST /api/admin/candidate-embeddings/{candidateProfileId}/rebuild
```

Optional:

```text
?force=true
```

---

# Matching API

## Run

```text
POST /api/matching/candidates/{candidateProfileId}
```

Default:

```text
force=false
```

Force:

```text
POST /api/matching/candidates/{candidateProfileId}?force=true
```

Response top level:

```json
{
  "candidateProfileId": "...",
  "candidateEmbeddingId": "...",
  "rankingVersion": "hybrid-v6-balanced-r4",
  "retrievedCount": 100,
  "loadedJobCount": 95,
  "matchedCount": 12,
  "reusedExisting": false,
  "results": []
}
```

Each result chứa:

```text
normalizedJobId
qdrantPointId
rank

job snapshot

finalScore
semanticScore
skillScore
seniorityScore
locationScore
freshnessScore

matchTier
explanations

matchedSkills
missingSkills

versions
generatedAt
```

---

## Current matching result

```text
GET /api/matching/candidates/{candidateProfileId}
```

Đọc result của:

```text
current READY candidate embedding
+
current ranking version
```

Không tự rerun nếu chưa tồn tại.

---

## Matching errors

Possible:

```text
401 MATCHING_AUTHENTICATION_REQUIRED

404 MATCHING_CANDIDATE_PROFILE_NOT_FOUND
404 MATCHING_RESULT_NOT_FOUND

409 MATCHING_CANDIDATE_EMBEDDING_NOT_READY
409 MATCHING_CANDIDATE_EMBEDDING_STALE
409 MATCHING_CANDIDATE_EMBEDDING_INVALID

503 MATCHING_VECTOR_STORE_UNAVAILABLE
```

---

# Job Crawler API

## Mock

```text
POST /api/admin/crawlers/mock/run
```

---

## Live

```text
POST /api/admin/crawlers/live/{sourceCode}/run
```

Query:

```text
limit=15
```

Supported:

```text
ITVIEC
JOBOKO
TOPDEV
VIECLAM24H
```

Maximum:

```text
50
```

Example:

```text
POST /api/admin/crawlers/live/ITVIEC/run?limit=10
```

---

## Raw jobs

```text
GET /api/raw-jobs?limit=20
```

Limit normalized to:

```text
1..100
```

---

# Parser fixture API

Dev/parser testing:

```text
POST /api/parsers/{sourceCode}/list-file
POST /api/parsers/{sourceCode}/detail-file
```

Đây là local/debug utility đọc HTML file từ filesystem của backend.

---

# Job Normalizer API

## Normalize one raw job

```text
POST /api/raw-jobs/{rawJobId}/normalize
```

Optional:

```text
?force=true
```

---

## Renormalize batch

```text
POST /api/admin/job-normalization/renormalize
```

---

## List normalized jobs

```text
GET /api/normalized-jobs
```

Query:

```text
page
size
sourceCode
normalizationVersion
```

Example:

```text
GET /api/normalized-jobs?page=0&size=20&sourceCode=ITVIEC
```

Max page size:

```text
100
```

---

## Normalized job detail

```text
GET /api/normalized-jobs/{id}
```

---

# Job Embedding API

## Latest

```text
GET /api/job-embeddings/{normalizedJobId}
```

---

## Rebuild

```text
POST /api/admin/job-embeddings/{normalizedJobId}/rebuild
```

Optional:

```text
?force=true
```

---

# Source Discovery

## Create website source

```text
POST /api/admin/source-discovery/website-sources
```

Body:

```json
{
  "sourceCode": "EXAMPLE",
  "domain": "example.com"
}
```

---

## Run

```text
POST /api/admin/source-discovery/website-sources/{id}/run
```

---

## Results

```text
GET /api/admin/source-discovery/website-sources/{id}/results
```

Current Source Discovery chỉ generate common candidate paths:

```text
/careers
/jobs
/tuyen-dung
/viec-lam
```

Nó chưa probe URL, validate response hoặc approve/reject source tự động.

---

# Health APIs

Java:

```text
GET /actuator/health
```

CV parser:

```text
GET http://localhost:8003/ready
```

Embedding:

```text
GET http://localhost:8002/ready
```