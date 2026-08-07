# Confidence Calculation Diagram

This diagram explains how RetrievalService computes confidence from retrieved chunks.

```mermaid
%%{init: {"flowchart": {"useMaxWidth": true, "nodeSpacing": 40, "rankSpacing": 50}, "themeVariables": {"fontSize": "16px"}} }%%
flowchart TD
    IN[Input question and retrieved chunks] --> E0{Chunks empty}
    E0 -->|yes| Z0[Confidence 0.00]

    E0 -->|no| M1[Compute max score from chunks]
    M1 --> M2[Compute mean score from chunks]
    M2 --> SC1[Schema coverage calculation]

    SC1 --> N1[Normalize question]
    N1 --> X1[Build expected schema refs from terms]
    X1 --> E1{Expected refs empty}

    E1 -->|yes| C0[Coverage 0.00]
    E1 -->|no| A1[Collect actual schema refs from chunks]
    A1 --> A2[Count covered expected refs]
    A2 --> C1[Coverage equals covered divided by expected]

    C0 --> F1[Raw confidence formula]
    C1 --> F1

    F1 --> F2[Raw equals max times 0.45 plus mean times 0.25 plus coverage times 0.30]
    F2 --> F3[Round to 2 decimals]
    F3 --> F4[Cap at 0.95]
    F4 --> OUT[Final confidence]
```

## Verbal Section

1. If no chunks are retrieved, confidence is immediately 0.00.
2. If chunks exist, the service computes two score statistics:
   - max score across chunks
   - mean score across chunks
3. The service also computes schema coverage from the question and retrieved schema references.
4. To compute coverage, the question is normalized first.
5. Expected schema references are inferred from keywords in the question:
   - customer, clients, region maps to customers
   - order, revenue, sales, spending maps to orders
   - product maps to products and order_items
6. If no expected references are inferred, coverage is 0.00.
7. Otherwise, coverage is covered_expected_refs divided by total_expected_refs.
8. Raw confidence is combined with weighted factors:
   - 45 percent from max score
   - 25 percent from mean score
   - 30 percent from schema coverage
9. The result is rounded to 2 decimals and capped at 0.95.
10. This final confidence is then used by retrieval flow to decide proceed or clarify.
