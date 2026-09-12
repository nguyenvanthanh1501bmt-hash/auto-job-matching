# Testing and Verification

AutoJob uses automated tests and manual verification across the Java backend, Python AI services, frontend, and integration flows.

The goal is to verify both individual modules and the full job-to-candidate matching pipeline.

---

## 1. Backend Tests

Backend tests are executed from:

```text
backend/
```

### macOS / Linux

```bash
./mvnw test
```

### Windows

```powershell
.\mvnw.cmd test
```

The Java test suite covers areas including:

* authentication and authorization;
* crawler parsing;
* job normalization;
* embedding integration;
* CV processing;
* candidate embeddings;
* matching;
* CV tailoring;
* AI provider routing;
* rewrite validation and safety.

Modules can also be tested independently when developing a specific feature.

---

## 2. CV Parser Tests

Location:

```text
ai-services/cv-parser-service
```

Install development dependencies:

```bash
python -m pip install -r requirements-dev.txt
```

Run tests:

```bash
python -m pytest
```

The parser test suite verifies:

```text
PDF / DOC / DOCX handling
section detection
identity and contact extraction
skills
experience
projects
education
languages
seniority
taxonomy integration
MinIO behavior
```

---

## 3. Embedding Service Tests

Location:

```text
ai-services/embedding-service
```

Install dependencies:

```bash
python -m pip install -r requirements.txt
```

Run:

```bash
python -m pytest
```

Important embedding contract values:

```text
Model     = intfloat/multilingual-e5-small
Dimension = 384
Normalized = true
```

Changes to embedding behavior should be validated against existing compatibility assumptions before old vectors are reused.

---

## 4. Frontend Verification

Location:

```text
frontend/web-app
```

Install dependencies:

```bash
npm ci
```

Lint:

```bash
npm run lint
```

Production build:

```bash
npm run build
```

A successful production build is recommended before merging frontend changes.

---

## 5. Integration Verification

After starting the local stack:

```bash
docker compose --env-file .env up -d --build
```

verify:

```text
GET http://localhost:8080/actuator/health
GET http://localhost:8002/ready
GET http://localhost:8003/ready
```

Expected result:

```text
Spring Boot       → healthy
Embedding service → ready
CV parser         → ready
```

---

## 6. Job Pipeline Verification

Recommended flow:

```text
Run mock crawler
    ↓
Verify raw_jobs
    ↓
Verify normalized_jobs
    ↓
Verify job_embeddings
    ↓
Verify Qdrant
```

Start the deterministic crawler:

```http
POST /api/admin/crawlers/mock/run
```

Then inspect normalized jobs:

```http
GET /api/normalized-jobs
```

Current expected versions:

```text
Normalization = rule-v4
Job text      = job-text-v2
```

---

## 7. Candidate Pipeline Verification

Recommended flow:

```text
Upload CV
    ↓
Parse CV
    ↓
Verify CandidateProfile
    ↓
Verify CandidateEmbedding
```

Endpoints:

```http
POST /api/cvs
POST /api/cvs/{rawCvId}/parse
GET  /api/cvs/{rawCvId}/profile
```

Expected candidate embedding version:

```text
candidate-text-v2
```

The embedding should eventually reach:

```text
READY
```

before matching is executed.

---

## 8. Matching Verification

Run:

```http
POST /api/matching/candidates/{candidateProfileId}?force=true
```

Verify:

```text
rankingVersion = hybrid-v6-balanced-r7
```

A valid result should include:

```text
rank
final score
component scores
matched skills
missing skills
match tier
explanations
```

Matching should not return incompatible or expired jobs merely to fill the result limit.

---

## 9. CV Tailoring Verification

Analyze a matched job:

```http
POST /api/cv-tailoring/candidates/{candidateProfileId}/jobs/{normalizedJobId}/analyze
```

Verify that suggestions are limited to:

```text
REWRITE
EMPHASIZE
GAP_WARNING
```

Then preview selected suggestions:

```http
POST /api/cv-tailoring/candidates/{candidateProfileId}/jobs/{normalizedJobId}/preview
```

The preview should:

* use a temporary candidate representation;
* generate a temporary embedding;
* re-evaluate the target job;
* return before/after scores;
* leave the persisted candidate profile unchanged.

---

## 10. Safety Verification

CV Tailoring should reject or avoid output that introduces unsupported information.

Important cases include:

```text
invented skills
invented technologies
invented certifications
invented education
unsupported metrics
unsupported dates
unsupported job titles
keyword stuffing
cross-node evidence leakage
```

AI provider success alone is not sufficient.

Returned content must still pass backend validation.

---

## 11. Pre-Merge Checklist

Before merging a significant change:

```text
Backend tests pass
Python tests pass when affected
Frontend lint passes
Frontend build passes
Docker services start successfully
Health checks pass
Relevant pipeline has been manually verified
Version changes are documented when required
```

Changes aff
