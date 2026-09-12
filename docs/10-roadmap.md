# Roadmap

AutoJob currently implements the complete MVP flow:

```text
Job Crawling
    ↓
Normalization
    ↓
Job Embeddings

CV Upload
    ↓
CV Parsing
    ↓
Candidate Embedding

Candidate + Jobs
    ↓
Hybrid Matching
    ↓
Explainable Results
    ↓
CV Tailoring
```

Future work focuses primarily on quality, reliability, observability, and production readiness rather than adding architectural complexity for its own sake.

---

## 1. Matching Quality

Priority improvements:

* expand anonymized evaluation datasets;
* build stable regression cases;
* measure ranking quality using metrics such as Precision@K and NDCG;
* analyze false-positive and false-negative matches;
* improve skill evidence weighting;
* improve location and work-mode compatibility;
* formalize ranking-version evaluation before rollout.

The goal is to make ranking changes measurable rather than subjective.

---

## 2. CV Parsing Quality

Future parser improvements may include:

```text
better complex-layout handling
stronger section detection
improved project extraction
better experience-duration calculation
improved multilingual CV support
higher-confidence seniority inference
```

Parser changes that materially affect candidate representations should continue to use explicit parser versions.

---

## 3. Embedding Reliability

Planned improvements:

* automatic stale-embedding detection;
* batch rebuild tooling;
* failed-embedding retry workflows;
* reconciliation between MongoDB and Qdrant;
* safer migration between embedding models or text versions.

Derived semantic data should remain rebuildable from canonical business data.

---

## 4. CV Tailoring

Potential improvements include:

```text
more precise evidence attribution
improved rewrite quality
additional provider evaluation
better suggestion prioritization
candidate-controlled suggestion acceptance
persistent tailored CV versions
export workflows
```

The core safety requirement remains unchanged:

> Tailoring may improve existing evidence but must not invent candidate qualifications.

---

## 5. Crawler Reliability

Planned improvements:

* source-specific monitoring;
* selector-change detection;
* retry and backoff policies;
* crawler scheduling;
* duplicate monitoring;
* per-source failure metrics;
* source health reporting.

Live crawling should remain isolated by source so failures do not affect the complete ingestion pipeline.

---

## 6. Observability

Production-oriented observability should include:

```text
structured logs
request correlation IDs
pipeline correlation IDs

crawler latency
parser latency
embedding latency
Qdrant latency
matching latency

failure rates
acceptance rates
ranking score distributions
```

Distributed tracing can be introduced when operational complexity justifies it.

---

## 7. Asynchronous Processing

Current Spring pipeline events are synchronous.

Potential future evolution:

```text
Job collected
    ↓
durable queue
    ↓
Normalization worker
    ↓
Embedding worker
```

and:

```text
Candidate profile ready
    ↓
durable queue
    ↓
Candidate embedding worker
```

A message broker should be introduced only when requirements such as:

```text
higher throughput
independent worker scaling
durable retries
failure isolation
```

make it necessary.

---

## 8. Security Hardening

Before production deployment:

* replace all development credentials;
* use managed secret storage;
* define key-rotation procedures;
* review JWT and refresh-token lifecycle;
* harden CORS configuration;
* add security regression testing;
* review file-upload protections;
* review rate-limit policies;
* review frontend token storage strategy.

Development defaults must not be considered production configuration.

---

## 9. Deployment

Future deployment work includes:

```text
production container images
CI/CD pipelines
environment-specific configuration
database backup strategy
MinIO/object-storage backup strategy
Qdrant rebuild strategy
health and readiness policies
rolling deployment strategy
```

Because job and candidate embeddings are derived data, deployment planning should distinguish between:

```text
canonical business data
and
rebuildable semantic indexes
```

---

## 10. Frontend

Potential frontend improvements include:

* stronger loading and error states;
* richer matching explanations;
* improved CV workspace;
* tailored CV editing and export;
* accessibility review;
* responsive UI refinement;
* end-to-end browser testing.

---

## 11. Engineering Principles

Future development should continue to preserve the following principles:

```text
Prefer explicit versions over hidden behavioral changes.

Prefer measurable ranking improvements over heuristic complexity.

Keep business state separate from derived semantic indexes.

Do not treat semantic similarity as a probability.

Do not fabricate missing candidate information.

Add distributed infrastructure only when operational requirements justify it.

Keep modules independently understandable and testable.
```

The goal is to evolve AutoJob from a strong full-stack MVP into a reliable production platform without losing the current clarity of its architecture.
