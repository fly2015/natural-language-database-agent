Dưới đây là đoạn tóm tắt thông tin đầy đủ, chuẩn hóa và cô đọng nhất. Bạn có thể sao chép đoạn này trực tiếp vào file **`README.md`**, file tài liệu kiến trúc (**`ARCHITECTURE.md`**) hoặc đưa vào Slide báo cáo của dự án:

---

# 📌 BẢN TÓM TẮT ĐẶC TẢ VÀ KIẾN TRÚC DỰ ÁN

## 🎯 1. TỔNG QUAN DỰ ÁN

* **Tên dự án:** Enterprise Natural Language Database Agent.
* **Mục tiêu:** Xây dựng hệ thống cho phép người dùng truy vấn Database bằng **ngôn ngữ tự nhiên** (Tiếng Việt, Tiếng Anh...). Hệ thống tự động đọc hiểu yêu cầu, tra cứu cấu trúc dữ liệu, sinh câu lệnh SQL, kiểm tra an toàn và thực thi lấy kết quả trực quan.
* **Thời gian thực hiện:** 2 tuần (Chiến lược làm theo từng giai đoạn).
* **Tech Stack:** Java (Spring Boot), LangChain4j, JDBC, Database (PostgreSQL / H2), LLM API (OpenAI / Gemini / Claude).

---

## 🛠️ 2. CÁC TÍNH NĂNG CỐT LÕI (CORE FEATURES)

1. **Dynamic Schema & Rules Retrieval (RAG):**
* **Tối ưu Token (Tiết kiệm 80 - 90% chi phí):** Dùng Vector Search (RAG) để chỉ lọc ra các bảng thực sự liên quan đến câu hỏi thay vì gửi toàn bộ Database Schema vào Prompt.
* Tra cứu các quy tắc nghiệp vụ nội bộ (business rules) để AI hiểu đúng ngữ cảnh.


2. **Text-to-SQL Generation:** Sinh câu lệnh SQL SELECT chuẩn xác từ câu hỏi ngôn ngữ tự nhiên.
3. **Database Guardrails (Bảo mật & An toàn):**
* Chặn 100% các câu lệnh can thiệp/phá hoại dữ liệu (`DELETE`, `DROP`, `UPDATE`, `INSERT`, `ALTER`).
* Tự động bổ sung giới hạn số dòng (như `LIMIT 100`) để tránh treo hệ thống.


4. **Data Execution & Visual Result:** Thực thi câu lệnh SQL an toàn qua JDBC và trả về kết quả bảng dữ liệu trực quan trên giao diện.

---

## 📐 3. HAI GIẢI PHÁP KIẾN TRÚC VÀ LỘ TRÌNH THỰC HIỆN

### 🟢 GIAI ĐOẠN 1: Kiến trúc Tích hợp Tập trung (Chưa dùng MCP) — *Ưu tiên triển khai Tuần 1*

* **Mô hình:** Tích hợp toàn bộ Web UI, RAG Engine, Guardrails và JDBC Tools trong **1 Project Spring Boot Monolith** sử dụng **LangChain4j**.
* **Mục tiêu:** Đảm bảo chắc chắn 100% có sản phẩm hoàn chỉnh, chạy ổn định để demo và nộp bài đúng hạn.

### 🔵 GIAI ĐOẠN 2: Kiến trúc Chuẩn hóa Cắm-và-Chạy (Có dùng MCP) — *Nâng cấp khi còn thời gian Tuần 2*

* **Mô hình:** Tách riêng toàn bộ RAG Engine + Guardrails + JDBC Tools thành một **Java MCP Server (Model Context Protocol)** độc lập chuẩn Anthropic.
* **Mục tiêu:** Biến Database thành "ổ cắm thông minh", cho phép bất kỳ MCP Client nào (Web UI hoặc ứng dụng **Claude Desktop** chính thức) kết nối cắm-và-chạy (Plug-and-Play) mà không cần viết lại code kết nối.