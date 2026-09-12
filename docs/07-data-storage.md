# Data Storage

AutoJob uses three primary storage technologies:

```text
MongoDB
Qdrant
MinIO
```

Each system has a separate responsibility.

```text
MongoDB → business data and application state
Qdrant  → semantic job retrieval
MinIO   → original CV files
```

---

## 1. MongoDB

Default local database:

```text
autojob
```

Main collections:

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

MongoDB is the primary source of truth for application business data.

---

## 2. Authentication Data

### `users`

Stores application users.

Main responsibilities:

```text
identity
normalized email
password hash
roles
account state
timestamps
```

Passwords are stored as BCrypt hashes rather than plaintext.

---

### `refresh_tokens`

Stores refresh-token sessions used for session rotation and revocation.

Refresh tokens are managed separately from short-lived JWT access tokens.

---

## 3. Job Data

### `raw_jobs`

Owner:

```text
job-crawler
```

Stores source-oriented job information collected by crawlers.

Typical data includes:

```text
source identity
source job ID
fingerprint

title
company
location
salary
experience
skills

job URLs
apply information

crawl timestamps
```

Raw job data represents information before canonical AutoJob normalization.

---

### `normalized_jobs`

Owner:

```text
job-normalizer
```

Current normalization version:

```text
rule-v4
```

Contains canonical job data such as:

```text
title
company

canonical skills
locations

salary range
experience range
seniority
job type

description
requirements
benefits

application metadata
posted date
deadline

normalization version
```

This is the primary job representation used by matching after Qdrant retrieval.

---

### `job_embeddings`

Owner:

```text
job-embedding
```

Stores job embedding lifecycle and synchronization metadata.

Typical fields:

```text
normalizedJobId
normalizationVersion

modelName
modelRevision
embeddingVersion

textHash
dimension
normalized

status

qdrantCollection
qdrantPointId

embeddedAt
lastError
```

Status values:

```text
PROCESSING
READY
FAILED
```

The searchable vector is indexed in Qdrant.

---

## 4. Candidate Data

### `raw_cvs`

Owner:

```text
cv
```

Stores metadata for uploaded CV files.

Typical fields include:

```text
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

uploadedAt
```

Status values:

```text
UPLOADED
PARSING
PARSED
FAILED
```

The original document itself is stored in MinIO.

---

### `candidate_profiles`

Owner:

```text
cv
```

Stores the structured candidate representation produced from the CV parser response.

Typical data includes:

```text
identity
contact information
links

headline
summary
career objective

skills
work experience
projects
education

certifications
licenses
languages

experience years
seniority

parser version
parse warnings
parse quality
source metadata
```

The Python parser returns structured data to Java.

Python does not directly write candidate profiles to MongoDB.

---

### `candidate_embeddings`

Owner:

```text
candidate-embedding
```

Stores the semantic representation of a candidate profile.

Current text representation:

```text
candidate-text-v2
```

Typical fields:

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
```

Status values:

```text
PROCESSING
READY
FAILED
```

Current vector dimension:

```text
384
```

Candidate vectors are stored in MongoDB and used directly as query vectors against the Qdrant job collection.

A separate candidate Qdrant collection is not required by the current architecture.

---

## 5. Matching Data

### `match_results`

Owner:

```text
matching
```

Stores accepted job matches for a candidate and ranking version.

Each record contains:

```text
candidate identity
candidate embedding identity

job identity
Qdrant point identity

job display snapshot

final score
semantic score
skill score
seniority score
location score
freshness score

component known/unknown state

matched skills
missing skills

parser version
normalization version
embedding version
candidate text version
job text version
ranking version

rank
generated timestamp
```

Current ranking version:

```text
hybrid-v6-balanced-r7
```

Version metadata allows historical matching results to remain distinguishable after scoring logic changes.

---

## 6. Source Discovery Data

### `website_sources`

Stores websites registered for source-discovery experiments.

### `source_discovery_results`

Stores discovery results associated with a registered source.

These collections support crawler/source exploration and are separate from the primary job pipeline.

---

## 7. Qdrant

Qdrant provides vector search for jobs.

Current collection:

```text
job_vectors_v1
```

Configuration:

```text
dimension = 384
distance  = Cosine
```

Each Qdrant point represents a job embedding and contains metadata used for compatibility filtering.

Typical metadata includes:

```text
normalizedJobId
normalizationVersion
embeddingVersion
textVersion
```

Qdrant is not the canonical job database.

Matching uses:

```text
Qdrant
   ↓
retrieve relevant job IDs
   ↓
MongoDB
   ↓
load complete normalized jobs
```

---

## 8. MinIO

MinIO stores original uploaded CV files.

Default local bucket:

```text
autojob-cvs
```

The bucket is private.

Typical flow:

```text
CV Upload
    ↓
Java validates file
    ↓
MinIO stores original object
    ↓
MongoDB stores RawCv metadata
    ↓
CV parser reads object
```

Separating binary storage from structured candidate data keeps MongoDB focused on application records rather than large documents.

---

## 9. CV Tailoring Data

CV-tailoring analysis sessions are currently temporary application state rather than permanent MongoDB records.

Flow:

```text
Analyze
    ↓
temporary analysisId
    ↓
user selects suggestions
    ↓
Preview
```

Preview applies selected suggestions to an in-memory candidate copy.

The persisted `candidate_profiles` document is not modified by preview.

---

## 10. Versioned Data

Several representations are explicitly versioned:

| Representation           | Current version         |
| ------------------------ | ----------------------- |
| CV parser                | `rule-v2`               |
| Job normalization        | `rule-v4`               |
| Job embedding text       | `job-text-v2`           |
| Candidate embedding text | `candidate-text-v2`     |
| Matching                 | `hybrid-v6-balanced-r7` |
| CV rewrite prompt        | `cv-rewrite-v1`         |

Versioning prevents stale or incompatible representations from being silently combined.

---

## 11. Storage Responsibilities

The overall ownership model is:

```text
Java / MongoDB
→ business state

Qdrant
→ searchable job vectors

MinIO
→ original candidate documents

Python services
→ computation only
```

Keeping these responsibilities explicit simplifies rebuilding derived data such as embeddings without losing canonical application state.
