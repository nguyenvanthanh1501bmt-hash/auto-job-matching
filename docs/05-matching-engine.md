# Matching Engine

The matching engine combines semantic retrieval with structured candidate–job compatibility to produce explainable job recommendations.

Current ranking version:

```text
hybrid-v6-balanced-r7
```

## 1. Overview

```text
Candidate Profile
        +
READY Candidate Embedding
        ↓
Qdrant Retrieval
        ↓
Normalized Job Hydration
        ↓
Eligibility Filtering
        ↓
Semantic Calibration
        ↓
Hybrid Scoring
        ↓
Acceptance Filtering
        ↓
Ranking
        ↓
Match Results
```

The system intentionally separates **retrieval** from **ranking**.

A high vector similarity does not automatically mean that a job is a strong match.

---

## 2. Matching API

Run matching:

```http
POST /api/matching/candidates/{candidateProfileId}
```

Force recalculation:

```http
POST /api/matching/candidates/{candidateProfileId}?force=true
```

Read the current result:

```http
GET /api/matching/candidates/{candidateProfileId}
```

Matching requires authentication and verifies ownership of the candidate profile.

---

## 3. Preconditions

Before matching can run, AutoJob requires:

```text
Candidate profile exists
        ↓
Candidate belongs to current user
        ↓
Candidate embedding exists
        ↓
Embedding status = READY
        ↓
Embedding version is compatible
        ↓
Vector is valid
```

Matching does not automatically parse CVs or rebuild missing embeddings.

---

## 4. Retrieval

Qdrant is used to retrieve semantically relevant job candidates.

Current configuration:

```yaml
candidate-pool-size: 100
result-limit: 20
```

Current compatibility requirements:

```text
normalizationVersion = rule-v4
candidateTextVersion = candidate-text-v2
jobTextVersion       = job-text-v2
```

Retrieval filters incompatible vectors before structured ranking begins.

---

## 5. Eligibility Filtering

Retrieved jobs may be rejected before ranking if they are no longer valid for the current matching run.

Examples include:

```text
Missing job
Version mismatch
Expired deadline
Job older than configured maximum age
Invalid job metadata
```

Current freshness configuration:

```text
Fresh job threshold = 7 days
Maximum age         = 30 days
```

Filtering invalid jobs before scoring prevents them from distorting semantic calibration and ranking.

---

## 6. Semantic Calibration

Raw cosine similarity is not treated as a direct match percentage.

The matching engine calibrates semantic scores relative to the retrieved candidate pool while preserving an absolute similarity signal.

Current configuration includes:

```text
Lower percentile = 0.10
Upper percentile = 0.90
Minimum spread   = 0.04

Raw floor        = 0.75
Raw ceiling      = 0.95

Relative weight  = 0.65
```

This reduces sensitivity to score distribution differences between candidate profiles.

---

## 7. Hybrid Scoring

Current component weights:

| Component           | Weight |
| ------------------- | -----: |
| Semantic relevance  |    40% |
| Skill compatibility |    40% |
| Seniority           |    10% |
| Location            |     5% |
| Freshness           |     5% |

The final score is calculated only from components for which meaningful evidence is available.

Conceptually:

```text
weighted known components
─────────────────────────
active component weights
```

Missing information is therefore not automatically treated as either a perfect match or a complete mismatch.

---

## 8. Skill Compatibility

Skill scoring uses the shared taxonomy under:

```text
configs/taxonomy/
```

The engine distinguishes between professional skills and secondary skills.

Examples of secondary skills include:

```text
communication
presentation
problem-solving
critical-thinking
teamwork
time-management
```

Secondary skills provide limited supporting evidence and cannot independently produce a strong professional match.

Candidate skill confidence also depends on where the evidence was found, such as:

```text
Skills section
Work experience
Projects
Profile text
```

Matching results expose:

```text
matchedSkills
missingSkills
```

Secondary skills are excluded from professional `missingSkills`.

---

## 9. Seniority

Seniority matching considers available evidence from both candidate and job data.

Candidate evidence may include:

```text
Parsed seniority
Experience years
Recent job titles
```

Job evidence may include:

```text
Normalized seniority
Minimum experience
Maximum experience
```

The scorer distinguishes between known evidence and unknown information instead of relying on title substring matching alone.

---

## 10. Acceptance Rules

Retrieval and ranking do not guarantee that a job will be returned.

Current acceptance thresholds include:

```text
Minimum final score    = 0.45
Minimum semantic score = 0.50
Minimum skill score    = 0.10

Strong skill score     = 0.70
Strong semantic score  = 0.70
```

This allows AutoJob to return fewer than the configured result limit when remaining jobs are not sufficiently relevant.

---

## 11. Explainability

Each result includes a score breakdown containing:

```text
final score
semantic score
skill score
seniority score
location score
freshness score
```

The response also identifies whether structured components had enough evidence to be considered known.

Additional explanation fields include:

```text
matchedSkills
missingSkills
explanations
version metadata
```

---

## 12. Match Tiers

For presentation purposes, results are categorized into:

```text
STRONG
STRETCH
POSSIBLE
EXPLORE
```

These tiers provide a user-friendly interpretation of the result.

They do not represent statistical probabilities and do not affect the underlying ranking order.

---

## 13. Persistence

Accepted results are stored in:

```text
match_results
```

Each persisted result records:

```text
candidate identity
candidate embedding
job identity
job snapshot

score breakdown
known/unknown component state

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

Persisting version metadata allows previous results to be distinguished from results created by newer matching logic.

---

## 14. Current Configuration

Matching configuration is maintained in:

```text
configs/matching/ranking.yml
```

Current version matrix:

| Component      | Version                 |
| -------------- | ----------------------- |
| Normalization  | `rule-v4`               |
| Candidate text | `candidate-text-v2`     |
| Job text       | `job-text-v2`           |
| Ranking        | `hybrid-v6-balanced-r7` |

Changes that materially affect result ordering should be accompanied by a ranking version bump.
