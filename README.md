# Cờ Thú Online (Jungle Chess / Dou Shou Qi)

A real-time multiplayer, PvE (vs Bot AI), and EvE (Bot vs Bot) web game of **Jungle Chess** built with Spring Boot, Angular, WebSocket (STOMP), and a standalone Minimax AI Bot Worker.

---

## 🛠️ Technology Stack

- **Backend (`be/`)**: Java 17+, Spring Boot 3+, Spring Security, Spring WebSocket (STOMP / SockJS), PostgreSQL, Redis, Flyway, jOOQ.
- **Bot Worker (`bot-worker/`)**: Java 17+, Spring Boot 3+, Alpha-Beta Pruning + PVS + LMR + Quiescence Search Bot Engine.
- **Frontend (`fe/`)**: Angular, RxJS, `@stomp/stompjs`, SVG/Canvas Board Renderer, CSS Animations.
- **Infrastructure**: Docker & Docker Compose.
- **Specification Workflow**: OpenSpec (Spec-Driven Development).

---

## 🚀 Quick Start & Development Commands

### 1. Prerequisites
Ensure you have the following tools installed on your machine:
- **Java 17+** (JDK 17, 21, or 25)
- **Node.js** (v18+) & **npm**
- **Docker** & **Docker Compose**

---

### 2. Step 1: Infrastructure Services (Database & Redis)
Start the PostgreSQL database (`jc-db`) and Redis (`jc-redis`) containers:
```bash
docker compose up -d
```
*(To stop containers: `docker compose down`)*

---

### 3. Step 2: Backend Setup & Run (`be/`)

Navigate to the `be/` directory:
```bash
cd be
```

- **Run DB Migrations & Generate jOOQ Code**:
  ```bash
  ./mvnw flyway:migrate jooq-codegen:generate
  ```

- **Start Backend Application**:
  ```bash
  ./mvnw spring-boot:run
  ```
  *(Backend runs at `http://localhost:8080`, Swagger UI at `http://localhost:8080/swagger-ui.html`)*

- **Run Backend Tests**:
  ```bash
  ./mvnw test
  ```
  *(Without live DB / jOOQ skipped: `./mvnw test -Djooq.codegen.skip=true`)*

---

### 4. Step 3: Bot Worker Setup & Run (`bot-worker/`)

The Bot Worker powers the AI opponent for PvE and EvE games using Minimax + Alpha-Beta Pruning.

In a new terminal, navigate to the `bot-worker/` directory:
```bash
cd bot-worker
```

- **Start Bot Worker Service**:
  ```bash
  ./mvnw spring-boot:run
  ```
  *(Bot Worker runs on port `8081` and connects to Backend WebSocket at `ws://localhost:8080/ws`)*

- **Run Bot Worker Tests**:
  ```bash
  ./mvnw test
  ```

---

### 5. Step 4: Frontend Setup & Run (`fe/`)

In a new terminal, navigate to the `fe/` directory:
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
  *(App opens in browser at `http://localhost:4200`)*

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
