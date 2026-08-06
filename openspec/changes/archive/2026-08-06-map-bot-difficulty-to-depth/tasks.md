## 1. Database: `bots` table

- [x] 1.1 Add Flyway migration `V<timestamp>__create_bots_table.sql` creating the `bots` table (`id UUID PK default gen_random_uuid()`, `name VARCHAR(50)`, `difficulty VARCHAR(20) CHECK (difficulty IN ('EASY','MEDIUM','HARD'))`, `search_depth INT`, `description TEXT`) plus a filtered check on difficulty and indexed lookup.
- [x] 1.2 Seed the three bot rows in the same migration (`EASY` depth 2, `MEDIUM` depth 4, `HARD` depth 6) per `docs/DB_Design_Co_Thu_Online.md` §3.3.
- [x] 1.3 Run `./mvnw flyway:migrate` against the local DB and verify the `bots` table + seed rows exist (manual check via `psql`).
- [x] 1.4 Run `./mvnw generate-sources` and confirm the jOOQ-generated `Bots` table + `BotsRecord` classes appear under `target/generated-sources/jooq`.

## 2. Bot difficulty model & mapping

- [x] 2.1 Add `BotDifficulty` enum (`EASY`, `MEDIUM`, `HARD`) with a `searchDepth` value per member (2/4/6) and a `setDefault`/`from(String)` that resolves unknown or null input to `MEDIUM`.
- [x] 2.2 Add `BotConfigService` that exposes `resolveDepth(BotDifficulty)`: looks up `BOTS` by difficulty via jOOQ, returns `search_depth`; falls back to the enum `searchDepth` when no row exists.
- [x] 2.3 Write unit test `BotDifficultyTest` verifying each difficulty maps to the correct default depth (EASY=2, MEDIUM=4, HARD=6) and unknown/null defaults to MEDIUM.

## 3. BotConfigService DB-backed resolution

- [x] 3.1 Implement the jOOQ lookup in `BotConfigService` using the generated `Bots` table, guarded so a missing table/row never throws.
- [x] 3.2 Write unit test `BotConfigServiceTest` (Mocked/embedded) verifying: row present → uses DB `search_depth`; row absent → uses enum default without error.
- [x] 3.3 Ensure the class is a Spring `@Component` so it can be injected by `RoomService`.

## 4. Room creation wiring

- [x] 4.1 In `RoomService` room-creation path for `PVE`/`EVE`, read `botDifficulty` from the create payload (tolerating absence → default).
- [x] 4.2 Resolve `searchDepth` once via `BotConfigService` and store it on the room/`RoomState`.
- [x] 4.3 When invoking `BotEngine.nextMove(board, side, depth)`, pass the resolved depth; keep the `BotEngine` signature unchanged.
- [x] 4.4 Write an integration test covering: creating a `PVE` (and `EVE`) room resolves the correct depth and the engine returns a legal move at each difficulty level.

## 5. Verification

- [x] 5.1 `./mvnw clean compile` passes (jOOQ generated `Bots` class compiles).
- [x] 5.2 `./mvnw test` passes all bot, rule, and new difficulty/config tests (28 total).
- [x] 5.3 Manual sanity: run the app, confirm room creation with `EASY`/`MEDIUM`/`HARD` stores the resolved `search_depth` on the room state. Room module is now implemented; runtime sanity requires Redis + PostgreSQL up.