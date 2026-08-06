# Text Normalization and Typo Correction Note

## Context
The current `normalize()` method in RetrievalService and other retrieval classes performs basic text cleanup:
- lowercase
- remove special characters/punctuation
- collapse whitespace

This is insufficient because retrieval must survive user typos and match schema names reliably before passing context to LLM.

## Problem
Current approach:
- `custmer revenue` stays as `custmer revenue` (no typo correction)
- simple regex replacement cannot recover from misspellings
- typo recovery is critical for schema name prediction before LLM

## Design Solution

### Layer 1: Text Normalization (Basic Cleanup)
Responsibility:
- lowercase
- Unicode normalization
- punctuation/special character removal
- whitespace cleanup

Class: `TextNormalizer` (shared utility)
Scope: English text only for MVP

### Layer 2: Retrieval Query Processing
Responsibility:
- normalize input
- tokenize into terms
- expand business aliases
- fuzzy-match user tokens against schema vocabulary
- emit corrected retrieval query

Class: `RetrievalQueryProcessor` (new component)
Input: user question
Output: `ProcessedQuery` containing:
- `original`: user input unchanged
- `normalized`: after text cleanup
- `correctedTerms`: typo-corrected tokens
- `retrievalQuery`: final query for RAG retrieval

### Layer 3: Schema Vocabulary Matching
Responsibility:
- maintain curated schema/table/column names
- fuzzy-match user tokens
- suggest corrections with confidence scores
- example: `custmer` → `customer` (confidence 0.95)

Class: `SchemaVocabularyMatcher` (new component)
Source: extract from JDBC schema metadata on startup

## Example Flow
User input: `top custmer spend by regoin`

1. Normalize: `top custmer spend by regoin`
2. Tokenize: `[top, custmer, spend, by, regoin]`
3. Fuzzy match against schema vocab:
   - `custmer` → `customer` (match with cost 1 edit)
   - `regoin` → `region` (match with cost 1 edit)
4. Expand aliases:
   - `customer` → stay
   - `spend` → `spend spending revenue`
5. Final retrieval query: `top custmer customer spend spending revenue by regoin region`

## Library Recommendation

### Option 1: Apache Commons Text (Lightweight)
Pros:
- minimal dependency
- good for Levenshtein distance / simple fuzzy matching
- low complexity
Cons:
- not optimized for search/retrieval
- no tokenization/stemming

### Option 2: Lucene Analyzers (Recommended)
Pros:
- built-in fuzzy query matching
- tokenization, stemming, analyzers
- industry-standard for retrieval
- good for future scale
Cons:
- heavier dependency
- more complex setup

### Option 3: ICU4J (Complementary)
Pros:
- proper Unicode normalization
- locale-aware text processing
Cons:
- not sufficient alone for typo recovery
- use alongside Commons Text or Lucene

**Recommendation for this project**: Start with Apache Commons Text for MVP, migrate to Lucene if retrieval quality becomes critical.

## Implementation Order
1. Create `TextNormalizer` (shared utility) — extract from RetrievalService
2. Create `RetrievalQueryProcessor` — use Commons Text for fuzzy matching
3. Refactor RetrievalService to use the two new components
4. Add SchemaVocabularyMatcher in Phase 2 if needed

## Dependencies to Add (When Ready)
```xml
<!-- For typo correction -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-text</artifactId>
    <version>1.11.0</version>
</dependency>
```

Or for advanced retrieval:
```xml
<!-- For advanced fuzzy retrieval -->
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-core</artifactId>
    <version>9.8.0</version>
</dependency>
```

## Decision Checkpoint
- **When**: Consider this after Phase 1 core MVP is working
- **Trigger**: If retrieval confidence drops or typos cause frequent NEEDS_CLARIFICATION responses
- **Timeline**: Phase 1.5 or Phase 2, not MVP blocking

## Current Workaround
Keep current simple normalization and `expandAliases()` for MVP.
Track retrieval success/failure rates to decide if typo correction is needed.
