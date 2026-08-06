# Gemini CLI Agent Instructions — Cờ Thú Online

This directory contains instructions and commands for **Gemini CLI** working on the Jungle Chess repository.

---

## Technical Context
- **Project**: Cờ Thú Online (Jungle Chess).
- **Backend (`be/`)**: Spring Boot, Java 17, STOMP WebSockets, PostgreSQL.
- **Frontend (`fe/`)**: Angular, RxJS, SVG Board UI.
- **Docs Reference**: Refer to [docs/](file:///home/khoicv/Work/Project/JungleChess/docs) for full specifications.
- **General Rules**: Follow [AGENTS.md](file:///home/khoicv/Work/Project/JungleChess/AGENTS.md) for GitFlow policies and reporting formats.

---

## OpenSpec Commands for Gemini CLI
- `/opsx:propose "feature description"` — Propose a new spec-driven change.
- `/opsx:apply` — Execute tasks from the active proposal.
- `/opsx:archive` — Archive completed changes.

---

## Verification
Always run test suites (`cd be && ./mvnw test` or `cd fe && npm test`) before reporting completion.
