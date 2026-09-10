# OpenAI Embedding Curl Commands

Use these commands from PowerShell to verify the app and the OpenAI embedding model used by the PostgreSQL retrieval profile.

## Check the Running App Configuration

```powershell
curl.exe http://localhost:8080/api/admin/retrieval-diagnostics
```

Expected fields:

```json
{
  "embeddingProvider": "openai",
  "embeddingModel": "text-embedding-3-small"
}
```

## Check Container Environment

Print only provider and model. Do not print the API key value.

```powershell
docker exec nlda-app-full-postgres printenv AGENT_RETRIEVAL_EMBEDDING_PROVIDER
docker exec nlda-app-full-postgres printenv AGENT_RETRIEVAL_EMBEDDING_MODEL
```

Expected output:

```text
openai
text-embedding-3-small
```

## Verify the OpenAI Embedding Model Directly

Load `OPENAI_API_KEY` from `.env` into the current PowerShell session:

```powershell
$env:OPENAI_API_KEY = (Get-Content .env | Select-String '^OPENAI_API_KEY=').Line.Split('=',2)[1]
```

Build the raw request body:

```powershell
$body = @'
{"model":"text-embedding-3-small","input":"test embedding request","dimensions":64}
'@
```

Optional: print the raw body before sending it.

```powershell
$body
```

Call the OpenAI embeddings API:

```powershell
curl.exe https://api.openai.com/v1/embeddings `
  -H "Authorization: Bearer $env:OPENAI_API_KEY" `
  -H "Content-Type: application/json" `
  --data-raw $body
```

If PowerShell strips quotes from the native curl argument, pipe the raw body through stdin instead:

```powershell
$body | curl.exe https://api.openai.com/v1/embeddings `
  -H "Authorization: Bearer $env:OPENAI_API_KEY" `
  -H "Content-Type: application/json" `
  --data-binary '@-'
```

Successful response shape:

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "embedding": [],
      "index": 0
    }
  ],
  "model": "text-embedding-3-small"
}
```

If the key or project cannot access the model, the response will contain an error such as `model_not_found` or an authentication error.

## Trigger and Verify Retrieval Index Rebuild

```powershell
curl.exe -X POST http://localhost:8080/api/admin/retrieval-index/rebuild
```

Expected successful response includes:

```json
{
  "outcome": "OK",
  "message": "retrieval indexes rebuilt"
}
```

Inspect stored pgvector rows:

```powershell
docker exec nlda-postgres-database-server psql -U nlda -d nlda_retrieval -c "select embedding_model, active, count(*) from retrieval_chunk_embedding group by embedding_model, active order by embedding_model, active;"
```

Verify one current row per chunk key:

```powershell
docker exec nlda-postgres-database-server psql -U nlda -d nlda_retrieval -c "select count(*) as rows, count(distinct (datasource_id, schema_fingerprint, chunk_id)) as distinct_chunk_keys from retrieval_chunk_embedding;"
```

## Run a Representative Query

Build the raw request body:

```powershell
$queryBody = @'
{"question":"list 10 customers"}
'@
```

```powershell
$queryBody | curl.exe -X POST http://localhost:8080/api/query `
  -H "Content-Type: application/json" `
  --data-binary '@-'
```

Expected successful response includes:

```json
{
  "status": "OK",
  "sql": "SELECT c.id AS customer_id, c.name AS customer_name, c.region, c.vip\nFROM customers c\nORDER BY c.id\nLIMIT 10"
}
```

For a metric query:

```powershell
$queryBody = @'
{"question":"Show top 10 customers by total spending in 2026"}
'@
```

```powershell
$queryBody | curl.exe -X POST http://localhost:8080/api/query `
  -H "Content-Type: application/json" `
  --data-binary '@-'
```

Expected metric response includes:

```json
{
  "status": "OK",
  "sql": "SELECT c.id AS customer_id, c.name AS customer_name, SUM(o.total_amount) AS total_spending ..."
}
```
