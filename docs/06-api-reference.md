# API Reference

Local API base URL:

```text
http://localhost:8080
```

Unless explicitly marked public, API requests require authentication.

Authenticated requests use:

```http
Authorization: Bearer <access-token>
```

---

## 1. Authentication

### Register

```http
POST /api/auth/register
```

### Login

```http
POST /api/auth/login
```

### Refresh session

```http
POST /api/auth/refresh
```

Refresh tokens are rotated when a new session is issued.

### Logout

```http
POST /api/auth/logout
```

### Current user

```http
GET /api/auth/me
```

`/api/auth/me` requires `USER` or `ADMIN`.

---

## 2. CV API

### Upload CV

```http
POST /api/cvs
Content-Type: multipart/form-data
```

Form field:

```text
file
```

Supported formats:

```text
PDF
DOC
DOCX
```

Maximum upload size:

```text
10 MB
```

---

### Get CV metadata

```http
GET /api/cvs/{rawCvId}
```

---

### Parse CV

```http
POST /api/cvs/{rawCvId}/parse
```

Successful parsing creates or updates the candidate profile and triggers candidate embedding generation.

---

### Get candidate profile

```http
GET /api/cvs/{rawCvId}/profile
```

CV endpoints enforce candidate ownership.

---

## 3. Jobs API

### List normalized jobs

```http
GET /api/normalized-jobs
```

Supported query parameters:

```text
page
size
sourceCode
normalizationVersion
```

Defaults:

```text
page = 0
size = 20
```

Maximum page size:

```text
100
```

Example:

```http
GET /api/normalized-jobs?page=0&size=20&sourceCode=ITVIEC
```

---

### Job detail

```http
GET /api/normalized-jobs/{id}
```

Normalized job APIs are available to `USER` and `ADMIN`.

---

## 4. Matching API

### Run matching

```http
POST /api/matching/candidates/{candidateProfileId}
```

Optional:

```text
?force=true
```

Without `force=true`, an existing compatible result may be reused.

---

### Get current matching result

```http
GET /api/matching/candidates/{candidateProfileId}
```

The response contains:

```text
candidateProfileId
candidateEmbeddingId
rankingVersion

retrievedCount
loadedJobCount
matchedCount
reusedExisting

results[]
```

Each result contains:

```text
job snapshot
rank

score breakdown
match tier
explanations

matched skills
missing skills

version metadata
generated timestamp
```

Current ranking version:

```text
hybrid-v6-balanced-r7
```

---

## 5. CV Tailoring API

A job must belong to the candidate's current matching result before it can be analyzed.

### Analyze CV for a job

```http
POST /api/cv-tailoring/candidates/{candidateProfileId}/jobs/{normalizedJobId}/analyze
```

Response includes:

```text
analysisId
expiresAt

job
currentMatch

evidence
suggestions
gaps
```

Suggestion types:

```text
REWRITE
EMPHASIZE
GAP_WARNING
```

---

### Preview selected suggestions

```http
POST /api/cv-tailoring/candidates/{candidateProfileId}/jobs/{normalizedJobId}/preview
```

Example request:

```json
{
  "analysisId": "analysis-id",
  "acceptedSuggestionIds": [
    "suggestion-id"
  ]
}
```

The response compares:

```text
before
after
```

matching scores using a temporary candidate representation.

Preview does not overwrite the persisted candidate profile.

---

## 6. Candidate Embedding Administration

Requires `ADMIN`.

### Get latest embedding

```http
GET /api/admin/candidate-embeddings/{candidateProfileId}
```

### Rebuild embedding

```http
POST /api/admin/candidate-embeddings/{candidateProfileId}/rebuild
```

Optional:

```text
?force=true
```

---

## 7. Job Embedding Administration

### Get latest job embedding

```http
GET /api/job-embeddings/{normalizedJobId}
```

Requires `ADMIN`.

### Rebuild job embedding

```http
POST /api/admin/job-embeddings/{normalizedJobId}/rebuild
```

Optional:

```text
?force=true
```

---

## 8. Crawler Administration

Requires `ADMIN`.

### Run mock crawler

```http
POST /api/admin/crawlers/mock/run
```

### Run live crawler

```http
POST /api/admin/crawlers/live/{sourceCode}/run
```

Optional query:

```text
limit=15
```

Maximum:

```text
50
```

Supported live sources:

```text
ITVIEC
JOBOKO
TOPDEV
VIECLAM24H
```

Example:

```http
POST /api/admin/crawlers/live/ITVIEC/run?limit=10
```

---

## 9. Raw Job Administration

Requires `ADMIN`.

### List raw jobs

```http
GET /api/raw-jobs?limit=20
```

Accepted limit:

```text
1..100
```

### Normalize one job

```http
POST /api/raw-jobs/{rawJobId}/normalize
```

Optional:

```text
?force=true
```

### Batch renormalization

```http
POST /api/admin/job-normalization/renormalize
```

---

## 10. Parser Utilities

Development and parser-testing endpoints:

```http
POST /api/parsers/{sourceCode}/list-file
POST /api/parsers/{sourceCode}/detail-file
```

These endpoints operate on local parser fixtures and require `ADMIN`.

They are intended as development utilities rather than candidate-facing APIs.

---

## 11. Source Discovery

Administrative source-discovery endpoints:

### Register source

```http
POST /api/admin/source-discovery/website-sources
```

### Run discovery

```http
POST /api/admin/source-discovery/website-sources/{id}/run
```

### Read results

```http
GET /api/admin/source-discovery/website-sources/{id}/results
```

---

## 12. Infrastructure Endpoints

Health:

```http
GET /actuator/health
```

OpenAPI:

```text
/v3/api-docs
```

Swagger UI:

```text
/swagger-ui/
```

Health and API documentation are public.

Other Actuator endpoints require `ADMIN`.

---

## 13. Authorization Summary

| API                                 | Access       |
| ----------------------------------- | ------------ |
| Register / Login / Refresh / Logout | Public       |
| Health / Swagger / OpenAPI          | Public       |
| `/api/auth/me`                      | USER / ADMIN |
| `/api/cvs/**`                       | USER / ADMIN |
| `/api/normalized-jobs/**`           | USER / ADMIN |
| `/api/matching/**`                  | USER / ADMIN |
| `/api/cv-tailoring/**`              | USER / ADMIN |
| `/api/admin/**`                     | ADMIN        |
| `/api/raw-jobs/**`                  | ADMIN        |
| `/api/parsers/**`                   | ADMIN        |
| `/api/job-embeddings/**`            | ADMIN        |

Any new `/api/**` route not explicitly configured in Spring Security is denied by default.
