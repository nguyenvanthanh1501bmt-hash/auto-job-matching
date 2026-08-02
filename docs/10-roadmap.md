# Roadmap

## Nguyên tắc

Roadmap ưu tiên hoàn thành pipeline end-to-end trước khi mở rộng số lượng tính năng.

Mỗi bước chỉ được xem là hoàn thành khi:

* Có implementation thực.
* Đã được nối vào `autojob-app` hoặc runtime tương ứng.
* Có persistence và trạng thái xử lý cần thiết.
* Có test hoặc command verification.
* Không chỉ tồn tại dưới dạng package, `pom.xml`, fixture hoặc placeholder.

## 1. Ổn định job pipeline

### Hiện tại

Đã có luồng hoạt động cho nguồn `MOCK`:

```text
raw_jobs
→ normalized_jobs
→ job_embeddings
→ Qdrant
```

### Kế hoạch

* Bổ sung integration test khởi động toàn `autojob-app`.
* Bổ sung reconciliation cho embedding `FAILED`.
* Chuẩn hóa logging và metrics của từng bước.
* Xác định lifecycle khi raw job hết TTL.
* Xử lý Qdrant point khi job bị disable hoặc xóa.
* Hoàn thiện idempotency cho Source Discovery.
* Bổ sung scheduler sau khi manual trigger ổn định.

### Điều kiện hoàn thành

* Chạy lại pipeline không tạo duplicate.
* Có thể recover sau khi embedding service hoặc Qdrant tạm thời lỗi.
* Có command xác minh MongoDB và Qdrant nhất quán.

## 2. Nối Java backend với CV parser

### Hiện tại

* CV upload đã lưu MinIO và `raw_cvs`.
* Python parser đã có implementation.
* Hai phía chưa giao tiếp.

### Kế hoạch

* Thêm `cv-parser-service` vào Docker Compose local.
* Thêm Java HTTP client.
* Thêm timeout và error mapping.
* Chuyển `raw_cvs.status` qua `PARSING`, `PARSED` hoặc `FAILED`.
* Lưu `lastError` khi parser lỗi.
* Bổ sung retry hoặc manual re-parse API.

### Điều kiện hoàn thành

```text
Upload CV
→ Java gọi parser
→ Java nhận parsed response
```

được verify bằng một command local.

## 3. Lưu candidate profile

### Kế hoạch

* Tạo `CandidateProfile` và repository.
* Lưu profile do Python parser trả về.
* Dùng logical key:

```text
rawCvId + parserVersion
```

* Validate `rawCvId`, parser version và giới hạn dữ liệu.
* Bổ sung API đọc candidate profile.
* Không để Python ghi trực tiếp MongoDB.

### Điều kiện hoàn thành

Upload một CV hợp lệ tạo được document trong:

```text
candidate_profiles
```

và có thể trace ngược về `raw_cvs`.

## 4. Tạo candidate embedding

### Kế hoạch

* Xây candidate `embeddingText`.
* Dùng cùng embedding model và dimension với job.
* Tạo `candidate_embeddings`.
* Lưu `embeddingVersion`, `textHash`, dimension và trạng thái.
* Hỗ trợ rebuild có version và idempotency.
* Quyết định có cần lưu candidate vector lâu dài trong Qdrant hay chỉ dùng để search.

### Điều kiện hoàn thành

Một candidate profile tạo được vector tương thích với:

```text
job_vectors_v1
```

## 5. Xây Matching Engine

### Hiện tại

Module `matching` mới là skeleton và chưa nằm trong runtime.

### Kế hoạch

* Thêm module vào Maven reactor.
* Thêm dependency vào `autojob-app`.
* Search topK job bằng candidate vector.
* Load `normalized_jobs` từ MongoDB.
* Implement:

```text
vectorScore
skillScore
seniorityScore
locationScore
freshnessScore
```

* Version hóa ranking formula.
* Lưu `match_results`.
* Trả matched skills, missing skills và lý do xếp hạng.
* Thêm test deterministic cho từng scorer.

### Điều kiện hoàn thành

```text
CV upload
→ parse
→ candidate embedding
→ Qdrant search
→ re-ranking
→ match_results
```

chạy được end-to-end.

## 6. Làm React frontend

### Hiện tại

`frontend/web-app` mới là placeholder.

### Kế hoạch

* Khởi tạo React, Vite và TypeScript.
* Register và login.
* Upload CV.
* Hiển thị trạng thái parse.
* Hiển thị candidate profile.
* Hiển thị matched jobs và explanation.
* Nút Apply mở `applyUrl` ở tab mới.
* Xử lý loading, failure và retry state.

Frontend không tự parse CV hoặc tự tính match score.

## 7. AI gợi ý sửa CV theo JD

### Kế hoạch

Chỉ triển khai sau khi Matching Engine ổn định.

Input:

```text
candidate profile
selected normalized job
match explanation
```

AI có thể:

* Gợi ý làm rõ kinh nghiệm đã có.
* Gợi ý keyword còn thiếu.
* Gợi ý cải thiện summary hoặc bullet point.
* Hỏi người dùng bổ sung evidence.

AI không được:

* Bịa kinh nghiệm.
* Bịa kỹ năng.
* Bịa dự án, công ty hoặc thành tích.
* Trình bày suggestion như dữ liệu đã được xác thực.

## 8. Hoàn thiện crawler website thật

### Hiện tại

Có parser fixture cho:

```text
ITVIEC
JOBOKO
TOPDEV
VIECLAM24H
```

Nhưng chỉ nguồn `MOCK` có live Camel route.

### Kế hoạch

Ưu tiên:

```text
Phase 1:
VIECLAM24H
JOBOKO
ITVIEC

Phase 2:
TOPDEV
JOBSGO
```

Với mỗi nguồn:

* Kiểm tra lại robots.txt.
* Xác minh HTML selector hiện tại.
* Thêm list/detail route.
* Rate limit theo domain.
* Dừng khi gặp 401, 403, 429, CAPTCHA hoặc login wall.
* Thêm fixture regression test.
* Theo dõi parser failure.
* Không bypass anti-bot.

Hoàn thiện Source Discovery trước khi crawl danh sách domain lớn.

## 9. Thêm RabbitMQ khi thật sự cần

### Hiện tại

Pipeline dùng Spring application events đồng bộ trong cùng JVM.

### Kế hoạch

Chỉ thêm RabbitMQ khi xuất hiện nhu cầu rõ ràng:

* CV parsing cần chạy background.
* Request upload không nên chờ parse và match.
* Cần persistent retry.
* Cần dead-letter queue.
* Embedding worker cần scale độc lập.
* Cần điều tiết tải Python services.

Các event contract phải giữ version rõ ràng.

Không thêm RabbitMQ chỉ để thay thế Spring event đang hoạt động tốt trong MVP.

## 10. Chatbot tùy chọn

### Kế hoạch

Chatbot có thể hỗ trợ:

* Giải thích vì sao một job được match.
* Tóm tắt điểm phù hợp và điểm còn thiếu.
* Hướng dẫn người dùng đọc recommendation.
* Hỏi thêm preference về location hoặc loại công việc.

Chatbot không phải dependency của:

```text
CV parsing
candidate embedding
vector search
hybrid ranking
```

Matching Engine phải hoạt động đầy đủ ngay cả khi không có chatbot.

## Thứ tự phụ thuộc

```text
Ổn định job pipeline
        ↓
Nối CV parser
        ↓
Lưu candidate profile
        ↓
Candidate embedding
        ↓
Matching Engine
        ↓
React frontend
        ↓
AI CV suggestions
```

Crawler website thật có thể được phát triển song song sau khi job pipeline và parser contract đã ổn định.

RabbitMQ và chatbot chỉ được thêm khi các pipeline cốt lõi đã chạy ổn định và có nhu cầu thực tế.
