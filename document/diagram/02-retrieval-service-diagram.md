# RetrievalService Diagram

This diagram shows how `RetrievalService` performs multi-step retrieval, confidence checks, and safe fallback.

```mermaid
%%{init: {"flowchart": {"useMaxWidth": true, "nodeSpacing": 40, "rankSpacing": 50}, "themeVariables": {"fontSize": "16px"}} }%%
flowchart TD
    Q[User question] --> RS[RetrievalService retrieve]

    RS --> A1[Attempt 1 NORMALIZED]
    A1 --> R1[SchemaRetriever retrieve NORMALIZED]
    R1 --> C1{confidence ge 0.65 and results exist}

    C1 -->|yes| P1[Proceed with ranked snippets]
    P1 --> OK1[Return proceed true]

    C1 -->|no| A2[Attempt 2 EXPANDED]
    A2 --> R2[SchemaRetriever retrieve EXPANDED]
    R2 --> C2{confidence ge 0.65 and results exist}

    C2 -->|yes| P2[Proceed]
    P2 --> OK2[Return proceed true]

    C2 -->|no| A3[Attempt 3 HYBRID]
    A3 --> R3[SchemaRetriever retrieve HYBRID]
    R3 --> C3{confidence ge 0.65 and results exist}

    C3 -->|yes| P3[Proceed]
    P3 --> OK3[Return proceed true]

    C3 -->|no| FB[Fallback cache attempt]
    FB --> FR[SchemaRetriever fallback]
    FR --> C4{confidence ge 0.65 and results exist}

    C4 -->|yes| P4[Proceed]
    P4 --> OK4[Return proceed true]

    C4 -->|no| CL[Clarify with safe stop and failure code]
    CL --> STOP[Return proceed false]

    E1[Runtime exception in retrieval] -.-> RF3[Mark failure code RF_03]
    R1 -. error .-> E1
    R2 -. error .-> E1
    R3 -. error .-> E1
    RF3 -. continue .-> A2

```

## Verbal Section

1. `RetrievalService` starts with a normalized query to improve match consistency.
2. If confidence is low or no chunks are found, it retries with alias expansion (`EXPANDED`).
3. If still weak, it performs a hybrid retrieval attempt (`HYBRID`).
4. If all three attempts fail to meet threshold, it falls back to cached/curated context (`FALLBACK_CACHE`).
5. Every attempt is logged with mode, confidence, failure code, and result count.
6. If any attempt reaches threshold and returns chunks, service returns `proceed = true` with ranked snippets.
7. If all attempts remain weak, service returns `proceed = false` with a safe clarification message.
8. Failure codes communicate why retrieval is blocked:
   - `RF_01`: no context found
   - `RF_02`: low confidence
   - `RF_03`: retrieval execution error
   - `RF_04`: post-validation recovery path triggered

## Current Wiring Note

`recoverFromValidationFailure` exists in `RetrievalService`, but it is currently only used in unit tests and is not called from the production orchestrator flow.
