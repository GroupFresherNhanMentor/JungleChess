# AI Agent Rules & Technical Standards — Cờ Thú Online (Jungle Chess)

This file defines the technical guidelines, engineering standards, GitFlow workflow, and OpenSpec Spec-Driven Development process for all AI coding agents working on the Jungle Chess project.

---

## 1. Project Overview & Architecture

- **Project**: Cờ Thú Online (Jungle Chess / Dou Shou Qi).
- **Backend (`be/`)**: Java / Spring Boot 3+, Spring Security, Spring WebSocket (STOMP / SockJS), Spring Data JPA, PostgreSQL.
- **Frontend (`fe/`)**: Angular, RxJS, `@stomp/stompjs`, SVG/Canvas UI rendering, CSS animations.
- **Realtime Protocol**: STOMP over WebSocket (Single source of truth on backend server).
- **Documentation**:
  - SRS & Requirements: [SRS_Co_Thu_Online.md](file:///home/khoicv/Work/Project/JungleChess/docs/SRS_Co_Thu_Online.md)
  - System Architecture: [Architecture_Co_Thu_Online.md](file:///home/khoicv/Work/Project/JungleChess/docs/Architecture_Co_Thu_Online.md)
  - Database Design & DDL: [DB_Design_Co_Thu_Online.md](file:///home/khoicv/Work/Project/JungleChess/docs/DB_Design_Co_Thu_Online.md)
  - API & WebSocket Contract: [API_Spec_Co_Thu_Online.md](file:///home/khoicv/Work/Project/JungleChess/docs/API_Spec_Co_Thu_Online.md)

---

## 2. Core Overarching Principles

1. **Do not fabricate evidence**: Never claim "tests passed", "build succeeded", or "verified" without running actual commands and observing output.
2. **Definition of Done**: A task is only "Done" when code builds cleanly, tests pass, and lint checks succeed.
3. **No hidden limitations or errors**: State unresolved issues or assumptions explicitly.
4. **Security & Secrets**: Never hardcode or log passwords, JWT secrets, tokens, or sensitive credentials.

---

## 3. Work Result Report Format

Each completed task or subtask report MUST follow this exact structure:

```
Done:        [what was completed and verified]
Not done:    [what was not done or not finished]
Blocked:     [reason if any, what info/decision is needed from the user]
Assumptions: [assumptions made when requirements were unclear]
Evidence:    [commands run, test results, relevant logs]
```

---

## 4. GitFlow Workflow & Branching Rules

- **`main`**: Production-ready code. **[FORBIDDEN]** Direct commits. Accepts merges only from `release/*` or `hotfix/*`.
- **`develop`**: Integration branch for next release. **[FORBIDDEN]** Direct commits. Accepts merges via PR from `feature/*`.
- **`feature/<ticket-id>-<description>`**: Feature development branch created from `develop`.
- **Pull Requests**: All PRs must target `develop`. Rebase/merge latest `develop` into `feature/*` before opening PR.
- **Forbidden Git Actions**: Never force-push to `main` or `develop`. Never auto-merge PRs without user confirmation.

---

## 5. Technical Guidelines

### Java / Spring Boot (Backend)
- **Layered Architecture**: `config`, `auth`, `room`, `game` (Rule Engine & Bot Engine), `history`, `common`.
- **Game Rule Engine**: Pure logic module independent of DB/WebSocket so it can be unit-tested cleanly.
- **Exception Handling**: Centrally handled (`@ControllerAdvice`, RFC 9457 `ProblemDetail`). Never catch empty `Exception e {}`.
- **Logging**: Log with correlation IDs at appropriate levels (`INFO`, `WARN`, `ERROR`). Never print stack traces to HTTP responses.

### Angular (Frontend)
- **Component Isolation**: Presentation components focus on UI; business & realtime state isolated in Services (`WebSocketService`, `GameStateService`).
- **Single Source of Truth**: State is driven by server STOMP broadcasts. Client rule logic is only used for temporary UI highlight.
- **Strict Typing & Clean-up**: Avoid `any`. Clean up RxJS subscriptions using `takeUntilDestroyed()` or AsyncPipe.

---

## 6. OpenSpec Spec-Driven Development Workflow

This repository uses **OpenSpec** (`openspec/`) to align on specifications before coding:

1. **Propose**: Create a proposal and design before implementation.
   - `/opsx-propose "description"` or `/opsx:propose "description"`
2. **Apply**: Implement tasks defined in `openspec/changes/<change-name>/tasks.md`.
   - `/opsx-apply` or `/opsx:apply`
3. **Archive**: Merge completed specs back into `openspec/specs/`.
   - `/opsx-archive` or `/opsx:archive`
