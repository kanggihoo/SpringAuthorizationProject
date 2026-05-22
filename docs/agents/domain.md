# Domain Docs

How engineering skills should consume this repo's domain documentation when exploring or changing the codebase.

## Layout

This repository uses a single-context layout.

- Root context: `CONTEXT.md`
- Architectural decisions: `docs/adr/`
- Context map: not configured

## Before Working

Read `CONTEXT.md` before work that touches domain language, authentication behavior, security policy, tests, or architecture.

Read ADRs in `docs/adr/` that touch the area you are about to work in. Existing decisions should shape implementation, naming, tests, and review comments.

If a referenced doc is missing, proceed silently. Do not create domain docs just because they are absent; create or update them only when the task explicitly resolves domain language or architectural decisions.

## Vocabulary

Use the terms from `CONTEXT.md` when naming concepts in code, tests, docs, issues, refactor proposals, and review findings.

If the concept you need is not in `CONTEXT.md`, treat that as a domain-language gap. Prefer noting the gap or using the closest existing term over inventing new vocabulary casually.

## ADR Conflicts

If a proposed change contradicts an ADR, surface the conflict explicitly before implementing or recommending the change.
