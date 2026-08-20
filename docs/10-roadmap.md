# Roadmap

## Current milestone

Core MVP backend pipeline hiện đã có:

```text
job crawler
→ job normalizer
→ job embedding
→ Qdrant

CV upload
→ CV parser
→ candidate profile
→ candidate embedding

candidate
→ hybrid matching
→ match_results
```

Roadmap không còn coi CV parser, candidate embedding hay Matching Engine là chưa triển khai.

---

# 1. Stabilize CV parsing

Current:

```text
Java ↔ cv-parser-service integrated
candidate_profiles persisted
candidate embedding triggered
real CV Docker verification available
```

Next:

```text
expand real-CV regression corpus
improve education parsing
improve certification/award section boundaries
improve multi-column extraction
improve non-canonical job-title detection
add more Vietnamese/English seniority regression cases
```

Seniority principles phải giữ:

```text
semantic > substring
current/latest > historical
structured history > aspiration
```

---

# 2. Expand Matching automated coverage

Matching runtime đã tồn tại nhưng automated test coverage còn mỏng.

Priority:

```text
SemanticScoreNormalizer
SkillScorer
SeniorityScorer
LocationScorer
FreshnessScorer
JobEligibilityFilter
MatchAcceptanceFilter
HybridRankingService
HybridMatchingService
```

Create golden regression cases từ real data.

---

# 3. Matching evaluation dataset

Build anonymized evaluation set:

```text
candidate profile
expected relevant job families
known false positives
known stretch opportunities
known seniority mismatch
known location mismatch
```

Metrics có thể theo dõi:

```text
Precision@K
Recall@K
NDCG
false-positive rate
manual relevance label
```

Không tune ranking chỉ bằng một CV.

---

# 4. Matching configuration governance

Current:

```text
hybrid-v6-balanced-r4
```

Mỗi thay đổi:

```text
weights
threshold
calibration
acceptance
skill confidence
```

phải bump:

```text
rankingVersion
```

để result cũ và result mới không bị lẫn.

---

# 5. Version consistency cleanup

Fix remaining config inconsistency:

```text
docker-compose fallback rule-v1
vs
current rule-v2
```

Target:

```text
.env.example
docker-compose.yml
application.yml
tests
```

dùng cùng parser version default.

---

# 6. Candidate embedding reliability

Current:

```text
PROCESSING
READY
FAILED
```

Next:

```text
automatic retry/reconciliation
metrics
failure dashboard
stale embedding reconciliation
batch rebuild
```

---

# 7. Job embedding reliability

Next:

```text
reconcile Mongo READY vs Qdrant point
rebuild missing points
remove stale/deleted job vectors
version migration tooling
```

---

# 8. Crawler production hardening

Live route hiện có:

```text
ITVIEC
JOBOKO
TOPDEV
VIECLAM24H
```

Next:

```text
selector monitoring
per-domain failure metrics
rate-limit/backoff
safe scheduler
dedup verification
HTML change alerts
source-specific integration checks
```

Không bypass:

```text
CAPTCHA
login wall
anti-bot protection
```

---

# 9. Source Discovery

Current Source Discovery chỉ generate common paths:

```text
/careers
/jobs
/tuyen-dung
/viec-lam
```

Next:

```text
HTTP probe
robots validation
content classification
candidate scoring
approve/reject workflow
crawler config generation
```

---

# 10. Frontend

Frontend cần hỗ trợ:

```text
register/login
CV upload
parse status
candidate profile
matching results
score explanations
matched skills
missing skills
match tier
apply URL
retry/error states
```

Frontend không tự tính match score.

---

# 11. Matching UX

Expose current:

```text
STRONG
STRETCH
POSSIBLE
EXPLORE
```

Improve explanation:

```text
why this job
matched core skills
missing key skills
seniority gap
location relation
freshness
```

Tránh hiển thị finalScore như một xác suất tuyệt đối.

---

# 12. AI CV suggestions

Chỉ build sau khi matching evaluation đủ ổn.

Input:

```text
candidate profile
selected normalized job
matching explanation
```

AI được phép:

```text
gợi ý wording
gợi ý keyword có evidence
gợi ý làm rõ experience
gợi ý bổ sung measurable detail
```

AI không được:

```text
bịa skill
bịa experience
bịa company
bịa achievement
```

---

# 13. Async processing

Hiện Spring events synchronous đủ cho local/MVP.

Chỉ thêm RabbitMQ/Kafka khi có nhu cầu:

```text
durable retries
background CV processing
independent worker scaling
DLQ
long-running workflows
```

Không thêm broker chỉ vì kiến trúc microservice-looking.

---

# 14. Observability

Add:

```text
structured logging
pipeline correlation id
parse latency
embedding latency
matching latency
Qdrant latency
crawler failure rate
ranking distribution
acceptance rate
```

---

# 15. Security

Before production:

```text
AUTH_PUBLIC_API_MODE=false
strong JWT secret
owner authorization regression tests
rate-limit tuning
MinIO credential rotation
non-default Mongo credentials
admin endpoint protection
```

---

# 16. Definition of core backend complete

Core backend được coi là ổn khi:

```text
job pipeline deterministic
CV pipeline deterministic
embedding recovery reliable
matching regression automated
real-data ranking quality measured
version migrations documented
Docker smoke test reproducible
```

Current architecture đã đi qua giai đoạn skeleton của:

```text
CV parser integration
candidate profile
candidate embedding
matching
```

Focus tiếp theo là:

```text
quality
regression coverage
reliability
frontend
production hardening
```