# Codecks Smart System

Smart is a local, deterministic suggestion layer. It helps the user find likely controls, but it never runs, edits, enables, or reorders anything by itself.

## Product rules

- Smart is default off.
- Smart starts on Deck only.
- Smart suggestions are temporary.
- Smart never rearranges pinned Deck buttons.
- Smart never adds a primary navigation tab.
- Smart never calls the LLM for ranking.
- Smart never bypasses normal review, test, confirmation, or execution gates.

## First release surface

Smart Deck shows a small suggestion row on Deck when both `SmartSuggestions` and `SmartDeck` are enabled.

Each suggestion supports:

- Run
- Pin
- Hide
- Why
- Never for this app

## Data flow

```text
Context repository
→ pure SmartContext
→ deterministic SmartEngine
→ temporary SmartCandidate list
→ Deck suggestion row
→ explicit user action
```

The Smart Engine proposes. Existing repositories and runners perform real work.
