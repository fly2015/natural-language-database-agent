Đã cập nhật lại chính xác theo yêu cầu của bạn! Cụm từ được chuẩn hóa thành **"Hệ thống truy vấn Database bằng ngôn ngữ tự nhiên"** (nghĩa là hệ thống có thể hỗ trợ cả Tiếng Việt, Tiếng Anh hoặc bất kỳ ngôn ngữ nào mà LLM hỗ trợ).

Dưới đây là bản tóm tắt hoàn chỉnh để bạn lưu lại:

---

## 📄 I. ĐẶC TẢ PROJECT: NATURAL LANGUAGE DATABASE AGENT

* **Tên dự án:** Enterprise Natural Language Database Agent (Hệ thống truy vấn Database bằng ngôn ngữ tự nhiên).
* **Mục tiêu chính:** Xây dựng ứng dụng cho phép người dùng gõ câu hỏi bằng **ngôn ngữ tự nhiên** $\rightarrow$ AI đọc hiểu, tự động tra cứu cấu trúc Database $\rightarrow$ Sinh câu lệnh SQL $\rightarrow$ Kiểm tra an toàn $\rightarrow$ Thực thi JDBC $\rightarrow$ Trả kết quả bảng dữ liệu trực quan lên giao diện.
* **Thời gian thực hiện:** 2 tuần.
* **Hệ sinh thái công nghệ:** Java (Spring Boot), JDBC / Database (PostgreSQL hoặc H2), LangChain4j, Mô hình AI (OpenAI / Gemini / Claude API).

### Các tính năng cốt lõi (Core Features):

1. **RAG (Schema & Rules Knowledge):** Cung cấp cấu trúc bảng/cột và các quy tắc nghiệp vụ (ví dụ: *Khách VIP = Chi tiêu > 50M*) cho AI hiểu đúng trước khi sinh SQL.
2. **Text-to-SQL Generation:** AI tự động viết câu lệnh SQL SELECT chuẩn xác dựa trên câu hỏi bằng ngôn ngữ tự nhiên.
3. **Guardrails (Bảo mật & An toàn):**
* Chặn hoàn toàn các câu lệnh can thiệp/phá hoại dữ liệu (`DELETE`, `DROP`, `UPDATE`, `INSERT`).
* Tự động thêm giới hạn số dòng (như `LIMIT 100`) để tránh treo Database.


4. **Data Execution & Visual Result:** Chạy SQL xuống Database qua JDBC và trả kết quả dưới dạng Bảng dữ liệu (Table/Markdown) lên UI.

---

## 📐 II. SO SÁNH 2 GIẢI PHÁP KIẾN TRÚC

```
  GỢI Ý LỘ TRÌNH CHIẾN LƯỢC (2 TUẦN)
  =============================================================================
  [Tuần 1 -> Đầu Tuần 2]  ──► GIẢI PHÁP 1: Chưa dùng MCP (Nộp bài an toàn 100%)
                                    │
                                    ▼ (Nếu còn thừa 1-2 ngày cuối)
  [Cuối Tuần 2]           ──► GIẢI PHÁP 2: Nâng cấp sang MCP (Thêm điểm cộng)
  =============================================================================

```

### 1. GIẢI PHÁP 1: Kiến trúc Tích hợp Tập trung (Chưa dùng MCP)

> **Mô hình Monolith tích hợp sẵn bằng LangChain4j**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ APPLICATION SPRING BOOT (MONOLITH)                                          │
│                                                                             │
│  [Web UI Chat] ──► [Agent Orchestrator] ──► [LangChain4j + AI Model API]    │
│                                                     │                       │
│                                     Gọi trực tiếp   ▼                       │
│                                          [Java JDBC Tools & RAG Engine]     │
└─────────────────────────────────────────────────────┬───────────────────────┘
                                                      │ (JDBC)
                                                      ▼
                                           [ Database H2 / Postgres ]

```

* **Cách hoạt động:**
* Toàn bộ UI, RAG Engine, Logic gọi AI (LangChain4j) và JDBC Tool chạy chung trong **1 Project Spring Boot duy nhất**.
* LangChain4j đóng vai trò kết nối trực tiếp các hàm Java `@Tool` với LLM qua cơ chế Function Calling / Tool Calling chuẩn của hãng (OpenAI, Gemini...).


* **Ưu điểm:**
* **Cực kỳ nhanh & Dễ triển khai:** Phù hợp nhất cho tiến độ 2 tuần, không tốn thời gian dựng giao thức mạng kết nối giữa các dịch vụ.
* Phù hợp làm sản phẩm nộp bài an toàn, chắc chắn demo thành công.


* **Nhược điểm:** Code bị đóng gói chặt (coupled), không chia sẻ được tính năng Query Database cho các ứng dụng client khác bên ngoài.

---

### 2. GIẢI PHÁP 2: Kiến trúc Chuẩn hóa Cắm-và-Chạy (Có dùng MCP)

> **Tách riêng Cửa ngõ Database thành Java MCP Server theo chuẩn Anthropic**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. MCP CLIENT / HOST (Web UI Spring Boot HOẶC App Claude Desktop)          │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ Giao thức MCP (JSON-RPC)
┌──────────────────────────────────────▼──────────────────────────────────────┐
│ 2. JAVA DATABASE MCP SERVER (Cửa ngõ dữ liệu độc lập)                       │
│    • Tool 1: get_schema_and_rules()  ──► [Embedded RAG Engine]             │
│    • Tool 2: execute_select_sql()    ──► [Guardrails + JDBC Engine]         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (JDBC)
                                       ▼
                            [ Database H2 / Postgres ]

```

* **Cách hoạt động:**
* **Java MCP Server:** Đóng gói RAG Engine + Guardrail + JDBC Tool thành 1 dịch vụ độc lập chạy SDK `mcp-java-sdk`. Dịch vụ này đóng vai trò "Ổ cắm Database" thông minh.
* **MCP Client / Host:** Là trang Web UI của bạn (dùng LangChain4j MCP Client) HOẶC ứng dụng **Claude Desktop chính thức**. Client chỉ việc "cắm" vào MCP Server để chat mà không cần biết cách truy vấn JDBC bên dưới ra sao.


* **Ưu điểm:**
* **Cắm-và-Chạy (Plug & Play):** Có thể kết nối trực tiếp với Claude Desktop mà không cần tốn công làm Web UI.
* **Tái sử dụng cao & Bảo mật tập trung:** Mọi ứng dụng AI trong doanh nghiệp muốn query Database đều có thể dùng chung 1 MCP Server này, mọi Guardrail được kiểm soát tại 1 nơi.


* **Nhược điểm:** Phải hiểu thêm về giao thức MCP (JSON-RPC, Stdio/SSE) và tốn thêm thời gian cấu hình kết nối Client - Server.