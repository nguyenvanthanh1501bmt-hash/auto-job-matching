# Troubleshooting

This document covers common issues when running AutoJob locally.

---

## 1. Backend Is Not Healthy

Check:

```bash
docker compose --env-file .env ps
```

Then inspect logs:

```bash
docker compose --env-file .env logs --tail=200 autojob-app
```

Common causes:

```text
MongoDB unavailable
Qdrant unavailable
invalid environment variables
JWT configuration issue
AI service unavailable
port conflict
```

Health endpoint:

```text
http://localhost:8080/actuator/health
```

---

## 2. Embedding Service Is Not Ready

Check:

```text
http://localhost:8002/ready
```

Logs:

```bash
docker compose --env-file .env logs -f embedding-service
```

The service may require additional startup time while loading:

```text
intfloat/multilingual-e5-small
```

Do not use container startup alone as the readiness signal.

---

## 3. CV Parser Is Not Ready

Check:

```text
http://localhost:8003/ready
```

Logs:

```bash
docker compose --env-file .env logs -f cv-parser-service
```

If parsing fails for one document, verify:

```text
supported file type
file size
document integrity
MinIO connectivity
parser logs
```

Supported formats:

```text
PDF
DOC
DOCX
```

---

## 4. Frontend Cannot Reach Backend

Verify:

```text
frontend/web-app/.env.local
```

contains:

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

Restart the Next.js development server after changing environment variables.

Also verify backend CORS configuration allows:

```text
http://localhost:5173
```

---

## 5. Requests Return 401

A `401 Unauthorized` usually means:

```text
access token expired
token missing
token invalid
refresh session expired
```

The frontend normally attempts automatic token refresh.

If refresh also fails:

```text
clear the current session
log in again
```

For direct API testing, ensure the request contains:

```http
Authorization: Bearer <access-token>
```

---

## 6. Requests Return 403

A `403 Forbidden` usually indicates insufficient permissions.

Administrative endpoints require:

```text
ADMIN
```

Examples:

```text
/api/admin/**
/api/raw-jobs/**
/api/parsers/**
/api/job-embeddings/**
```

Normal registration creates a:

```text
USER
```

account.

---

## 7. Matching Cannot Run

Matching requires a candidate embedding with:

```text
status = READY
```

Check the candidate embedding through the administration endpoint.

Common causes:

```text
CV not parsed
candidate embedding still processing
candidate embedding failed
embedding version mismatch
invalid vector
candidate ownership mismatch
```

Matching does not automatically repair missing upstream data.

---

## 8. Matching Returns No Jobs

An empty result is not necessarily an error.

Jobs may be rejected because of:

```text
low semantic relevance
low skill compatibility
expired deadline
job age
version mismatch
acceptance thresholds
```

AutoJob intentionally prefers returning fewer valid matches over padding the result list with weak jobs.

For debugging, inspect:

```text
retrievedCount
loadedJobCount
matchedCount
```

in the matching response.

---

## 9. Jobs Exist but Are Missing From Qdrant

Check whether job embeddings are:

```text
READY
```

and whether their version matches the current pipeline.

Current expected values include:

```text
normalization = rule-v4
job text      = job-text-v2
dimension     = 384
```

The embedding may need to be rebuilt after a version change.

---

## 10. Qdrant Collection Problems

Current collection:

```text
job_vectors_v1
```

Inspect collections:

```text
http://localhost:6333/collections
```

Expected vector dimension:

```text
384
```

If collection configuration does not match the embedding model, recreate or rebuild local vector data rather than mixing incompatible vectors.

---

## 11. Live Crawler Stops Working

Live crawlers depend on third-party websites.

Common causes include:

```text
HTML structure changed
CSS selectors changed
redirect behavior changed
rate limiting
temporary remote failure
anti-bot protection
```

First verify the deterministic:

```text
MOCK
```

crawler.

If the mock pipeline works but a live source fails, the issue is likely source-specific.

Do not attempt to bypass CAPTCHA, authentication walls, or anti-bot protections.

---

## 12. CV Tailoring Returns No AI Rewrite

AI rewriting requires:

```text
CV_TAILORING_AI_ENABLED=true
```

and at least one configured provider.

Supported providers currently include:

```text
Groq
Gemini
```

If an AI provider is unavailable, AutoJob may still return deterministic:

```text
EMPHASIZE
GAP_WARNING
```

suggestions.

This fallback is expected behavior.

---

## 13. CV Tailoring Preview Fails

Check that:

```text
analysisId is still valid
job belongs to current match results
suggestion IDs belong to the analysis
candidate profile still exists
candidate can be embedded
```

Tailoring analyses are temporary and should not be treated as permanent stored records.

---

## 14. Port Conflicts

Default local ports:

```text
5173  frontend
8080  backend
8002  embedding service
8003  CV parser
27018 MongoDB
6333  Qdrant HTTP
6334  Qdrant gRPC
9000  MinIO
9001  MinIO Console
18080 mock job site
```

Check whether another process already uses the required port before changing application configuration.

---

## 15. Reset Local Environment

Restart services:

```bash
docker compose --env-file .env down
docker compose --env-file .env up -d --build
```

For a full local data reset:

```bash
docker compose --env-file .env down -v
docker compose --env-file .env up -d --build
```

> Removing volumes deletes local MongoDB, Qdrant, and MinIO data.

Use this only when a clean environment is intended.
