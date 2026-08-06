# Antigravity & Codex Agent Instructions — Cờ Thú Online

This directory contains customization rules and skills for **Antigravity** and **Codex** agents working on the Jungle Chess project.

---

## Workspace Context
- **Root Instructions**: Refer to [AGENTS.md](file:///home/khoicv/Work/Project/JungleChess/AGENTS.md) for full GitFlow rules, work report formatting, Java/Angular standards, and core principles.
- **Specifications**: Refer to [docs/](file:///home/khoicv/Work/Project/JungleChess/docs) for SRS, Architecture, DB schema, and API contracts.

---

## OpenSpec Commands & Workflow
When executing OpenSpec tasks:
- **Propose new change**: `/opsx-propose "feature description"`
- **Apply task implementation**: `/opsx-apply`
- **Sync main specs**: `/opsx-sync`
- **Archive completed change**: `/opsx-archive`

---

## Quality & Verification
- Build Backend: `cd be && ./mvnw clean test`
- Build Frontend: `cd fe && npm test`
- Always state verified command results in the **Evidence** section of your task report.
