# Cờ Thú Online (Jungle Chess / Dou Shou Qi)

A real-time multiplayer, PvE (vs Bot AI), and EvE (Bot vs Bot) web game of **Jungle Chess** built with Spring Boot, Angular, and WebSocket (STOMP).

---

## 🛠️ Technology Stack

- **Backend (`be/`)**: Java 17+, Spring Boot 3+, Spring Security, Spring WebSocket (STOMP / SockJS), PostgreSQL, Redis, Flyway, jOOQ.
- **Frontend (`fe/`)**: Angular, RxJS, `@stomp/stompjs`, SVG/Canvas Board Renderer, CSS Animations.
- **Infrastructure**: Docker & Docker Compose.
- **Specification Workflow**: OpenSpec (Spec-Driven Development).

---

## 🚀 Quick Start & Development Commands

### 1. Prerequisites
Ensure you have Docker, Java 17+, and Node.js installed on your machine.

### 2. Infrastructure Services (Database & Redis)
Start the PostgreSQL database and Redis containers:
```bash
docker compose up -d
```
*(To stop containers: `docker compose down`)*

---

### 3. Backend Setup & Commands (`be/`)

Navigate to the `be/` directory:
```bash
cd be
```

- **Run DB Migrations & Generate jOOQ Code**:
  ```bash
  ./mvnw flyway:migrate jooq-codegen:generate
  ```

- **Run All Unit Tests**:
  ```bash
  ./mvnw test
  ```

- **Run Unit Tests (without live DB / jOOQ skipped)**:
  ```bash
  ./mvnw test -Djooq.codegen.skip=true
  ```

- **Start Backend Application**:
  ```bash
  ./mvnw spring-boot:run
  ```

---

### 4. Frontend Setup & Commands (`fe/`)

Navigate to the `fe/` directory:
```bash
cd fe
```

- **Install Dependencies**:
  ```bash
  npm install
  ```

- **Start Frontend Development Server**:
  ```bash
  npm start
  ```
  *(App opens at `http://localhost:4200`)*

- **Run Frontend Tests**:
  ```bash
  npm test
  ```

---

## 📖 Specifications & Architecture Docs

Detailed documentation is available in the [`docs/`](./docs) directory:
- [SRS_Co_Thu_Online.md](./docs/SRS_Co_Thu_Online.md) — Software Requirements Specification & International Rules
- [Architecture_Co_Thu_Online.md](./docs/Architecture_Co_Thu_Online.md) — System Architecture & Layer Diagram
- [DB_Design_Co_Thu_Online.md](./docs/DB_Design_Co_Thu_Online.md) — Database Schema, ERD & DDL Scripts
- [API_Spec_Co_Thu_Online.md](./docs/API_Spec_Co_Thu_Online.md) — REST & WebSocket STOMP Message Contracts

---

## ⚡ OpenSpec Spec-Driven Development Workflow

This repository uses **OpenSpec** for change management and spec alignment:
- Propose new feature: `/opsx-propose "feature description"`
- Implement change: `/opsx-apply`
- Sync main specs: `/opsx-sync`
- Archive completed change: `/opsx-archive`
