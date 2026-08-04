const form = document.querySelector("#queryForm");
const input = document.querySelector("#questionInput");
const submitButton = document.querySelector("#submitButton");
const conversation = document.querySelector("#conversation");
const resultPanel = document.querySelector("#resultPanel");
const tableWrap = document.querySelector("#tableWrap");
const sqlPanel = document.querySelector("#sqlPanel");
const toggleSql = document.querySelector("#toggleSql");
const traceId = document.querySelector("#traceId");
const latency = document.querySelector("#latency");
const connectionStatus = document.querySelector("#connectionStatus");

toggleSql.addEventListener("click", () => {
    sqlPanel.hidden = !sqlPanel.hidden;
});

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const question = input.value.trim();
    if (!question) {
        return;
    }

    appendMessage("You", question, "user");
    setBusy(true);

    try {
        const response = await fetch("/api/query", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({question})
        });
        const data = await response.json();
        renderResponse(data);
        input.value = "";
    } catch (error) {
        appendMessage("Agent", "The request could not be completed safely.", "assistant error");
        connectionStatus.textContent = "Request failed";
    } finally {
        setBusy(false);
    }
});

function renderResponse(data) {
    traceId.textContent = data.traceId ? `Trace ${data.traceId}` : "No trace";
    latency.textContent = `${data.latencyMs ?? 0} ms`;

    if (data.status === "OK") {
        connectionStatus.textContent = "Ready";
        appendMessage("Agent", data.answer || "Query completed.", "assistant");
        renderTable(data.table);
        sqlPanel.textContent = data.sql || "";
        resultPanel.hidden = false;
        return;
    }

    connectionStatus.textContent = "Rejected";
    appendMessage("Agent", data.reason || "The query was rejected.", "assistant error");
    resultPanel.hidden = true;
}

function renderTable(table) {
    const columns = table?.columns || [];
    const rows = table?.rows || [];
    if (columns.length === 0 || rows.length === 0) {
        tableWrap.innerHTML = '<div class="empty-table">No rows returned.</div>';
        return;
    }

    const thead = `<thead><tr>${columns.map(column => `<th>${escapeHtml(column)}</th>`).join("")}</tr></thead>`;
    const tbody = rows.map(row => {
        const cells = columns.map(column => `<td>${escapeHtml(formatValue(row[column]))}</td>`).join("");
        return `<tr>${cells}</tr>`;
    }).join("");
    tableWrap.innerHTML = `<table>${thead}<tbody>${tbody}</tbody></table>`;
}

function appendMessage(author, body, className) {
    const article = document.createElement("article");
    article.className = `message ${className}`;
    const meta = document.createElement("div");
    meta.className = "message-meta";
    meta.textContent = author;
    const messageBody = document.createElement("div");
    messageBody.className = "message-body";
    messageBody.textContent = body;
    article.append(meta, messageBody);
    conversation.append(article);
    conversation.scrollTop = conversation.scrollHeight;
}

function setBusy(isBusy) {
    input.disabled = isBusy;
    submitButton.disabled = isBusy;
    connectionStatus.textContent = isBusy ? "Running" : connectionStatus.textContent;
}

function formatValue(value) {
    if (value === null || value === undefined) {
        return "";
    }
    return String(value);
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
