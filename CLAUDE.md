# CLAUDE.md — Claude Code Instructions

## Overview
This is the repository for **Cờ Thú Online (Jungle Chess)**, built with **Spring Boot** backend and **Angular** frontend.

---

## Technical Stack & Architecture
- **Backend (`be/`)**: Java 17 / Spring Boot 3+, WebSocket STOMP, PostgreSQL.
- **Frontend (`fe/`)**: Angular, RxJS, `@stomp/stompjs`, SVG board UI.
- **Architecture**: Single source of truth on backend server. REST for auth (`/api/auth/*`), WebSockets for gameplay (`/app/room/*`, `/topic/room/*`).
- **Docs**: Specifications located in [docs/](file:///home/khoicv/Work/Project/JungleChess/docs).

---

## Useful Commands

### Backend (`be/`)
- Build project: `./mvnw clean compile`
- Run unit tests: `./mvnw test`
- Run application: `./mvnw spring-boot:run`

### Frontend (`fe/`)
- Install dependencies: `npm install`
- Start dev server: `npm run start`
- Run tests: `npm test`
- Production build: `npm run build`

---

## OpenSpec Workflow Commands
This project uses OpenSpec for Spec-Driven Development:
- Propose change: `/opsx:propose "feature description"`
- Apply change tasks: `/opsx:apply`
- Archive completed change: `/opsx:archive`

---

## Engineering & Git Flow Rules
- Refer to [AGENTS.md](file:///home/khoicv/Work/Project/JungleChess/AGENTS.md) for full project rules, GitFlow branching model (`feature/*` -> `develop`), and mandatory Work Result Report format (`Done`, `Not done`, `Blocked`, `Assumptions`, `Evidence`).
