const statusLine = document.getElementById('statusLine');

document.querySelectorAll('.tab').forEach((button) => {
    button.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach((tab) => tab.classList.remove('active'));
        document.querySelectorAll('.panel').forEach((panel) => panel.classList.add('hidden'));
        button.classList.add('active');
        document.getElementById(`${button.dataset.tab}Panel`).classList.remove('hidden');
    });
});

document.getElementById('ruleForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    await postJson('/api/admin/business-rules', {
        id: form.get('id'),
        name: form.get('name'),
        text: form.get('text'),
        owner: form.get('owner'),
        version: Number(form.get('version') || 1),
        datasourceId: form.get('datasourceId'),
        tenantId: form.get('tenantId'),
        schemaRefs: csv(form.get('schemaRefs')),
        aliases: csv(form.get('aliases'))
    });
    event.currentTarget.reset();
    await loadRules();
});

document.getElementById('refreshRules').addEventListener('click', loadRules);
document.getElementById('refreshSchema').addEventListener('click', async () => {
    await postJson('/api/admin/schema/refresh', {});
    await loadSchema();
    await loadIndex();
});
document.getElementById('rebuildRules').addEventListener('click', async () => {
    await postJson('/api/admin/retrieval-index/business-rules/rebuild', {});
    await loadIndex();
});
document.getElementById('rebuildAll').addEventListener('click', async () => {
    await postJson('/api/admin/retrieval-index/rebuild', {});
    await loadIndex();
});
document.getElementById('refreshDiagnostics').addEventListener('click', loadDiagnostics);

async function loadRules() {
    const rules = await getJson('/api/admin/business-rules');
    const table = document.getElementById('rulesTable');
    table.innerHTML = '';
    for (const rule of rules) {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeHtml(rule.id)}</td>
            <td>${escapeHtml(rule.approvalStatus)} ${rule.active ? '' : '(inactive)'}</td>
            <td>${escapeHtml(rule.owner)}</td>
            <td>${rule.version}</td>
            <td>${escapeHtml(rule.datasourceId || '')}/${escapeHtml(rule.tenantId || '')}</td>
            <td>
                <button type="button" data-action="approve" data-id="${escapeHtml(rule.id)}">Approve</button>
                <button type="button" data-action="deactivate" data-id="${escapeHtml(rule.id)}">Deactivate</button>
                <button type="button" data-action="reindex" data-id="${escapeHtml(rule.id)}">Re-index</button>
            </td>
        `;
        table.appendChild(row);
    }
    table.querySelectorAll('button').forEach((button) => {
        button.addEventListener('click', async () => {
            const action = button.dataset.action;
            const id = button.dataset.id;
            await postJson(`/api/admin/business-rules/${id}/${action}`, {});
            await loadRules();
            await loadIndex();
        });
    });
}

async function loadSchema() {
    const schema = await getJson('/api/admin/schema');
    document.getElementById('schemaFingerprint').textContent = schema.fingerprint;
    document.getElementById('schemaTableCount').textContent = schema.tableCount;
}

async function loadIndex() {
    const records = await getJson('/api/admin/retrieval-index');
    const table = document.getElementById('indexTable');
    table.innerHTML = '';
    for (const record of records) {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeHtml(record.chunkId)}</td>
            <td>${escapeHtml(record.kind)}</td>
            <td>${escapeHtml(record.embeddingModel || '')}</td>
            <td>${record.active}</td>
            <td>${escapeHtml(record.indexedAt || '')}</td>
        `;
        table.appendChild(row);
    }
}

async function loadDiagnostics() {
    const diagnostics = await getJson('/api/admin/retrieval-diagnostics');
    document.getElementById('diagnosticsOutput').textContent = JSON.stringify(diagnostics, null, 2);
}

async function getJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(await response.text());
    }
    return response.json();
}

async function postJson(url, body) {
    statusLine.textContent = 'Working';
    const response = await fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(body)
    });
    statusLine.textContent = response.ok ? 'Ready' : 'Error';
    if (!response.ok) {
        throw new Error(await response.text());
    }
    return response.json();
}

function csv(value) {
    return String(value || '')
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean);
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

Promise.all([loadRules(), loadSchema(), loadIndex(), loadDiagnostics()])
    .catch((error) => {
        statusLine.textContent = error.message;
    });
