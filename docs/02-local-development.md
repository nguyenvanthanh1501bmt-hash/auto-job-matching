# Local Development

This guide describes the recommended local setup for AutoJob.

## Prerequisites

Required:

* Docker and Docker Compose
* Node.js
* npm

Java and Python do not need to be installed locally when backend services are run through Docker Compose.

---

## 1. Environment Setup

From the repository root, create the local environment file:

### Windows PowerShell

```powershell
Copy-Item .env.example .env
```

### macOS / Linux

```bash
cp .env.example .env
```

The values provided in `.env.example` are intended for local development only.

Do not reuse default credentials, JWT secrets, or API keys in production.

---

## 2. Start Backend Services

Run:

```bash
docker compose --env-file .env up -d --build
```

Check service status:

```bash
docker compose --env-file .env ps
```

The local stack includes:

| Service           | Address                  |
| ----------------- | ------------------------ |
| AutoJob API       | `http://localhost:8080`  |
| Embedding Service | `http://localhost:8002`  |
| CV Parser         | `http://localhost:8003`  |
| MongoDB           | `localhost:27018`        |
| Qdrant            | `http://localhost:6333`  |
| MinIO API         | `http://localhost:9000`  |
| MinIO Console     | `http://localhost:9001`  |
| Mock Job Site     | `http://localhost:18080` |

---

## 3. Health Checks

Backend:

```bash
curl http://localhost:8080/actuator/health
```

Embedding service:

```bash
curl http://localhost:8002/ready
```

CV parser:

```bash
curl http://localhost:8003/ready
```

The embedding service loads the configured Sentence Transformer model during startup, so readiness may take longer than container startup.

---

## 4. Start the Frontend

Create:

```text
frontend/web-app/.env.local
```

with:

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

Then:

```bash
cd frontend/web-app
npm ci
npm run dev
```

Open:

```text
http://localhost:5173
```

---

## 5. Authentication

Most business APIs require authentication.

Main endpoints:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```

Authenticated requests use:

```http
Authorization: Bearer <access-token>
```

Current roles:

```text
USER
ADMIN
```

New registrations receive the `USER` role.

Administrative operations such as crawler execution, raw-job management, normalization rebuilds, and embedding administration require `ADMIN`.

---

## 6. Basic Development Flow

A typical local test flow is:

```text
Start Docker services
        ↓
Start Next.js frontend
        ↓
Register / Login
        ↓
Crawl or load jobs
        ↓
Upload and parse CV
        ↓
Generate candidate embedding
        ↓
Run matching
        ↓
Analyze CV tailoring
```

### Run mock crawler

```http
POST /api/admin/crawlers/mock/run
```

### Upload CV

```http
POST /api/cvs
```

Multipart field:

```text
file
```

### Parse CV

```http
POST /api/cvs/{rawCvId}/parse
```

### Run matching

```http
POST /api/matching/candidates/{candidateProfileId}
```

Force recalculation:

```http
POST /api/matching/candidates/{candidateProfileId}?force=true
```

---

## 7. Current Version Configuration

The current development stack uses:

| Component                | Version                          |
| ------------------------ | -------------------------------- |
| CV Parser                | `rule-v2`                        |
| Job Normalization        | `rule-v4`                        |
| Job Embedding Text       | `job-text-v2`                    |
| Candidate Embedding Text | `candidate-text-v2`              |
| Matching                 | `hybrid-v6-balanced-r7`          |
| Embedding Model          | `intfloat/multilingual-e5-small` |
| Vector Dimension         | `384`                            |
| Qdrant Collection        | `job_vectors_v1`                 |

These versions form part of the matching compatibility contract.

---

## 8. Logs

View all service logs:

```bash
docker compose --env-file .env logs -f
```

Backend only:

```bash
docker compose --env-file .env logs -f autojob-app
```

CV parser:

```bash
docker compose --env-file .env logs -f cv-parser-service
```

Embedding service:

```bash
docker compose --env-file .env logs -f embedding-service
```

---

## 9. Rebuild a Service

Example:

```bash
docker compose --env-file .env up -d --build autojob-app
```

or:

```bash
docker compose --env-file .env up -d --build cv-parser-service
```

---

## 10. Stop the Environment

```bash
docker compose --env-file .env down
```

To also remove local Docker volumes:

```bash
docker compose --env-file .env down -v
```

Use `-v` only when local MongoDB, MinIO, and Qdrant data can be safely deleted.
