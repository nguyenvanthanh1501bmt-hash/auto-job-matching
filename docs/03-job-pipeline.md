# Job Pipeline

The job pipeline converts source-specific job postings into normalized, searchable job data used by the matching engine.

## Overview

```text
Job Source
    ↓
Crawler
    ↓
Raw Job
    ↓
Normalization
    ↓
Normalized Job
    ↓
Embedding
    ↓
Qdrant
    ↓
Matching
```

Each stage is separated and versioned so jobs can be reprocessed without repeating the entire pipeline.

---

## 1. Job Sources

Current crawler sources:

```text
MOCK
ITVIEC
JOBOKO
TOPDEV
VIECLAM24H
```

`MOCK` uses the local mock job site and provides deterministic data for development and testing.

Live crawlers use source-specific Apache Camel routes and parsers.

---

## 2. Crawling

Administrative crawler endpoints:

### Mock crawler

```http
POST /api/admin/crawlers/mock/run
```

### Live crawler

```http
POST /api/admin/crawlers/live/{sourceCode}/run?limit=15
```

Supported live sources:

```text
ITVIEC
JOBOKO
TOPDEV
VIECLAM24H
```

The maximum live crawl limit is currently:

```text
50
```

Crawler endpoints require the `ADMIN` role.

---

## 3. Raw Job Storage

Crawler output is stored in MongoDB:

```text
raw_jobs
```

A raw job preserves source-oriented information such as:

```text
source
source job ID
title
company
location
salary text
experience text
skills
job URL
apply information
timestamps
```

This layer represents what was collected from the source before AutoJob applies its canonical business model.

---

## 4. Normalization

After a raw job is stored, the crawler publishes:

```text
JobRawCollectedEvent
```

The normalizer then creates a canonical representation:

```text
RawJob
    ↓
JobRawCollectedEvent
    ↓
JobNormalizationService
    ↓
NormalizedJob
```

Normalized jobs are stored in:

```text
normalized_jobs
```

Current normalization version:

```text
rule-v4
```

Normalization covers fields such as:

* canonical skills;
* secondary skills;
* location;
* salary;
* experience requirements;
* seniority;
* job type;
* posting date;
* deadline;
* application information.

Shared taxonomies are stored under:

```text
configs/taxonomy/
```

---

## 5. Job Embedding

After successful normalization:

```text
JobNormalizedReadyEvent
```

triggers the embedding stage.

```text
NormalizedJob
    ↓
JobNormalizedReadyEvent
    ↓
JobEmbeddingService
    ↓
Embedding Service
    ↓
MongoDB + Qdrant
```

Current job embedding text version:

```text
job-text-v2
```

Embedding model:

```text
intfloat/multilingual-e5-small
```

Vector dimension:

```text
384
```

---

## 6. Vector Index

Job vectors are indexed in Qdrant.

Current collection:

```text
job_vectors_v1
```

Distance metric:

```text
Cosine
```

MongoDB remains the source of truth for job data.

Qdrant is used as the semantic retrieval index.

The matching engine first retrieves relevant job IDs from Qdrant and then loads the complete normalized job documents from MongoDB.

---

## 7. Version Compatibility

Job vectors include metadata used to prevent incompatible representations from being mixed.

Important values include:

```text
normalizationVersion
embeddingVersion
textVersion
```

Current matching expects:

```text
normalizationVersion = rule-v4
textVersion          = job-text-v2
```

Vectors produced by incompatible versions should be rebuilt before being used by the current matching configuration.

---

## 8. Administration

List raw jobs:

```http
GET /api/raw-jobs
```

Normalize a specific raw job:

```http
POST /api/raw-jobs/{rawJobId}/normalize
```

Run batch renormalization:

```http
POST /api/admin/job-normalization/renormalize
```

List normalized jobs:

```http
GET /api/normalized-jobs
```

Read normalized job detail:

```http
GET /api/normalized-jobs/{id}
```

Job embeddings can also be inspected or rebuilt through the embedding administration APIs.

---

## 9. Design Notes

The job pipeline intentionally separates:

```text
source extraction
business normalization
semantic representation
```

This makes it possible to:

```text
recrawl without changing ranking logic
renormalize without recrawling
rebuild vectors without recrawling
change matching logic without changing stored source data
```

Live crawler behavior depends on third-party websites and may require maintenance when external HTML structures change.
