# Data Storage

## 1. Storage systems

AutoJob sử dụng:

```text
MongoDB
Qdrant
MinIO
```

---

# MongoDB

Database local:

```text
autojob
```

Collections:

```text
users
refresh_tokens

raw_jobs
normalized_jobs
job_embeddings

raw_cvs
candidate_profiles
candidate_embeddings

match_results

website_sources
source_discovery_results
```

---

## raw_jobs

Owner:

```text
job-crawler
```

Purpose:

```text
structured data từ source website
fingerprint
source identity
raw retention metadata
```

Raw HTML/text mặc định không cần persist cho live crawler.

Event:

```text
JobRawCollectedEvent
```

---

## normalized_jobs

Owner:

```text
job-normalizer
```

Current normalization:

```text
rule-v4
```

Contains:

```text
normalized title/company
skills
salary
locations
experience range
seniority
job type
description
requirements
benefits
apply metadata
dates
normalization version
```

Logical versioning dựa trên:

```text
rawJobId
normalizationVersion
```

---

## job_embeddings

Owner:

```text
job-embedding
```

Contains:

```text
normalizedJobId
normalizationVersion
textVersion
embeddingVersion
textHash
dimension
normalized
status
qdrantCollection
qdrantPointId
timestamps
lastError
```

Status:

```text
PROCESSING
READY
FAILED
```

Vector job authoritative search copy nằm ở Qdrant.

---

## raw_cvs

Owner:

```text
cv
```

Contains:

```text
id
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

Status:

```text
UPLOADED
PARSING
PARSED
FAILED
```

---

## candidate_profiles

Owner:

```text
cv
```

Created by:

```text
cv-parser-service response
→ Java validation/mapping
→ Mongo persistence
```

Python không ghi trực tiếp Mongo.

Contains structured CV data:

```text
identity
contact
links

career objective
headline
summary

skills
work experiences
projects
education
certifications

experienceYears
seniority

recent job titles
recent companies

parser version
parser warnings
parse quality
raw text
source metadata
```

---

## candidate_embeddings

Owner:

```text
candidate-embedding
```

Contains:

```text
candidateProfileId
rawCvId
parserVersion
textVersion

modelName
modelRevision
embeddingVersion
textHash

dimension
normalized
vector

status
embeddedAt
lastError
timestamps
```

Current:

```text
textVersion = candidate-text-v1
dimension   = 384
```

Candidate vector hiện được persist trong Mongo và dùng trực tiếp làm query vector cho Qdrant job search.

Không cần candidate collection riêng trong Qdrant.

---

## match_results

Owner:

```text
matching
```

Contains:

```text
rawCvId
candidateProfileId
candidateEmbeddingId

normalizedJobId
qdrantPointId

job display snapshot

parserVersion
normalizationVersion
embeddingVersion
candidateTextVersion
jobTextVersion
rankingVersion

rank

finalScore
semanticScore
skillScore
seniorityScore
locationScore
freshnessScore

matchedSkills
missingSkills

generatedAt
```

Unique:

```text
candidateProfileId
candidateEmbeddingId
rankingVersion
normalizedJobId
```

Index:

```text
candidateProfileId
rankingVersion
rank
```

---

## website_sources

Source Discovery input.

Current status lifecycle includes states such as:

```text
PENDING_DISCOVERY
DISCOVERING
DISCOVERED
NO_CANDIDATE_FOUND
```

---

## source_discovery_results

Candidate URLs generated từ:

```text
/careers
/jobs
/tuyen-dung
/viec-lam
```

Current result status:

```text
PENDING_REVIEW
```

Source Discovery hiện chưa phải full crawler source onboarding engine.

---

# Qdrant

Collection:

```text
job_vectors_v1
```

Dimension:

```text
384
```

Distance:

```text
Cosine
```

Job point metadata dùng cho compatibility filtering:

```text
normalizedJobId
normalizationVersion
embeddingVersion
textVersion
```

Matching search filter các version này trước khi hydrate Mongo documents.

---

# MinIO

Bucket:

```text
autojob-cvs
```

Private bucket.

Object pattern:

```text
raw/yyyy/MM/dd/{rawCvId}/{safeFilename}
```

MinIO chỉ giữ source CV binary.

Business parsed state nằm trong MongoDB.

---

# Version consistency

Important:

```text
CV parser             rule-v2
Job normalization     rule-v4
Candidate text        candidate-text-v1
Job text              job-text-v2
Matching              hybrid-v6-balanced-r4
```

Version fields được persist để tránh ranking giữa incompatible artifacts.

---

# Ownership

Trong local public mode:

```text
ownerUserId = public-local-user
```

Khi auth public mode tắt:

```text
ownerUserId = authenticated principal
```

Matching validate candidate ownership trước khi đọc embedding/search job.