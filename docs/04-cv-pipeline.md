# CV Pipeline

The CV pipeline converts an uploaded document into a structured candidate profile and semantic representation that can be used by the matching engine.

## Overview

```text
CV Upload
    ↓
MinIO
    ↓
Raw CV Metadata
    ↓
CV Parser
    ↓
Candidate Profile
    ↓
Candidate Embedding
    ↓
Matching
```

---

## 1. CV Upload

Endpoint:

```http
POST /api/cvs
```

Request type:

```text
multipart/form-data
```

File field:

```text
file
```

Supported formats:

```text
PDF
DOC
DOCX
```

Current maximum file size:

```text
10 MB
```

Original CV files are stored privately in MinIO.

Default bucket:

```text
autojob-cvs
```

Metadata is stored in MongoDB:

```text
raw_cvs
```

---

## 2. CV Processing Status

A raw CV moves through the following states:

```text
UPLOADED
    ↓
PARSING
    ↓
PARSED
```

Failures are represented by:

```text
FAILED
```

CV metadata can be retrieved through:

```http
GET /api/cvs/{rawCvId}
```

---

## 3. CV Parsing

Parsing is triggered explicitly:

```http
POST /api/cvs/{rawCvId}/parse
```

Flow:

```text
Spring Boot
    ↓
CV Parser Service
    ↓
read original file from MinIO
    ↓
extract document text
    ↓
detect sections
    ↓
normalize candidate data
    ↓
return structured profile
```

Current parser version:

```text
rule-v2
```

The Python parser handles document extraction and candidate-information parsing.

Java remains responsible for business persistence.

---

## 4. Candidate Profile

Parsed profiles are stored in:

```text
candidate_profiles
```

The structured profile can contain:

* name and headline;
* contact information;
* professional summary;
* career objective;
* skills;
* work experience;
* projects;
* education;
* certifications and licenses;
* languages;
* links;
* preferred locations and work modes;
* experience years;
* seniority;
* parser warnings;
* parse quality.

Retrieve the parsed profile with:

```http
GET /api/cvs/{rawCvId}/profile
```

---

## 5. Candidate Embedding

After the candidate profile is successfully persisted, AutoJob publishes:

```text
CandidateProfileReadyEvent
```

This triggers:

```text
CandidateProfile
    ↓
CandidateProfileReadyEvent
    ↓
CandidateEmbeddingService
    ↓
Embedding Service
    ↓
candidate_embeddings
```

Current candidate text representation:

```text
candidate-text-v2
```

Embedding model:

```text
intfloat/multilingual-e5-small
```

Expected dimension:

```text
384
```

---

## 6. Candidate Embedding Status

Candidate embeddings use:

```text
PROCESSING
READY
FAILED
```

Matching requires a compatible embedding in:

```text
READY
```

state.

A failed embedding does not invalidate the parsed candidate profile.

This allows embedding generation to be retried without reparsing the CV.

---

## 7. Matching Handoff

Once the candidate embedding is ready:

```text
Candidate Profile
        +
Candidate Embedding
        ↓
Qdrant job retrieval
        ↓
Hybrid Matching
        ↓
Match Results
```

Matching does not automatically parse an unprocessed CV or create missing candidate data.

The CV pipeline must complete successfully before matching can run.

---

## 8. Parser Safety and Limits

The CV parser applies limits to document processing to protect the service from unexpectedly large or malformed files.

Examples include limits for:

```text
file size
PDF page count
extracted text size
section size
DOC/DOCX processing
number of parsed entities
```

The parser also records warnings and parse-quality information rather than assuming every CV can be extracted perfectly.

---

## 9. Separation of Responsibilities

The pipeline deliberately separates three representations:

### Raw CV

```text
original uploaded document
```

Stored in MinIO with metadata in MongoDB.

### Candidate Profile

```text
structured business representation
```

Stored in:

```text
candidate_profiles
```

### Candidate Embedding

```text
semantic representation
```

Stored in:

```text
candidate_embeddings
```

This separation allows parsing, candidate modeling, and semantic search to evolve independently.

---

## 10. Current Flow

The implemented end-to-end candidate flow is:

```text
Upload CV
    ↓
Store in MinIO
    ↓
Create raw_cvs record
    ↓
Parse document
    ↓
Create candidate_profiles record
    ↓
Publish CandidateProfileReadyEvent
    ↓
Generate candidate embedding
    ↓
Store candidate_embeddings record
    ↓
Run hybrid matching
    ↓
Optional CV tailoring
```

This is the current supported path for turning a candidate CV into job matching results.
