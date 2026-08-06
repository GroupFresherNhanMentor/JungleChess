## Context

The bot engine (`BotEngine.nextMove(Board, Side, int depth)`) is implemented and tested but only invoked with a raw `depth`. Players select `botDifficulty` (`EASY`/`MEDIUM`/`HARD`) in the room-creation payload (`docs/API_Spec_Co_Thu_Online.md` §3.3.1), yet nothing maps that value to a depth. There is no room module yet that resolves bot config and calls the engine. See proposal.md — Why.

Key constraint discovered: the `bots` table from `docs/DB_Design_Co_Thu_Online.md` §3.3 **does not exist** in the current schema. Existing Flyway migrations only create `users`, `roles`, `user_roles`; jOOQ has generated `Users`, `Roles`, `UserRoles` tables only. So difficulty config cannot be read from the DB until the `bots` table is created and seeded.

Also note: the implemented `users` table differs from the docs (it carries `employee_id`, `status`, and a `BOT` role) — the design must not assume the documented `users` shape applies to bot configuration. The `bots` table is a standalone lookup and is what this change introduces.

## Goals / Non-Goals

**Goals:**
- Map `BotDifficulty` → `searchDepth` with a documented default (`EASY=2`, `MEDIUM=4`, `HARD=6`).
- Resolve bot config from the `bots` table when present, falling back to defaults otherwise.
- Wire the resolved depth into room creation for `PVE`/`EVE` so the engine is called with the correct depth.
- Keep `BotEngine` free of difficulty/DB knowledge (single responsibility preserved).

**Non-Goals:**
- PvE/EvE *turn orchestration* (looping bot moves, delays) — that is the Room module owner's work, not this change.
- Multiple bot "personalities" or rating/ELO systems.
- Frontend (Angular) changes — `botDifficulty` is already part of the FE→BE contract; FE is not in scope here.
- Changing the existing `users` table or the `BOT` role semantics.

## Decisions

**D1 — Introduce a `BotDifficulty` enum.**
`fpt.qn.junglechess.game.bot.BotDifficulty` with values `EASY`, `MEDIUM`, `HARD` and an int `searchDepth` field per value, plus a `static BotDifficulty from(String)` that is tolerant of null/unknown by defaulting to `MEDIUM`.
*Rationale:* a single source of the mapping that is unit-testable and shared, matches the seed data, and mirrors the documented `difficulty` column values. _Alternative: keep a raw `Map<BotDifficulty,Integer>` in the service_ — but an enum carries the canonical default and is easier to test.

**D2 — Add a `BotConfigService` that reads the `bots` table via jOOQ with an in-code fallback.**
`BotConfigService.resolveDepth(BotDifficulty)`: query `BOTS` by `difficulty`, return `search_depth` if a row exists, else return the enum default.
*Rationale:* aligns with "server single source of truth + DB-backed config" and the existing jOOQ (`Repository`, `UserRepository`) pattern. _Alternative: hardcode depths in the service_ — simpler but ignores the documented `bots` table and fails the spec's "prefer table, fall back" requirement.

**D3 — Create and seed the `bots` table via a new Flyway migration, then regenerate jOOQ.**
A new `V<timestamp>__create_bots_table.sql` creating `bots (id UUID PK, name, difficulty CHECK (EASY|MEDIUM|HARD), search_depth INT, description)` and seeding the three rows. After `flyway:migrate`, run `./mvnw generate-sources` so jOOQ surfaces the `Bots` table. *Alternative: seed via `DataInitializer`* — rejected: the docs specify seed data in the DB and other lookup data is seeded by migration/initializer already; a migration makes config explicit and versioned.

**D4 — `RoomService` resolves config and passes depth at room creation.**
When a room is created in mode `PVE`/`EVE`, `RoomService` reads `botDifficulty` from the create payload, calls `BotConfigService.resolve`, and stores the resolved `searchDepth` on the `RoomState`; subsequent `BotEngine.nextMove(board, side, depth)` calls use it. `BotEngine` method signature is unchanged (still `int depth`), keeping the interface stable per the archived `bot-engine` spec.
*Rationale:* resolution happens once at creation, not per move — cheap and consistent.

## Decisions (dependencies)

- The `bots` table shape follows `docs/DB_Design_Co_Thu_Online.md` §3.3 (columns `id`, `name`, `difficulty`, `search_depth`, `description`), adapted to this codebase's conventions (`UUID` PK, `TIMESTAMPTZ`).

## Risks / Trade-offs

- [jOOQ codegen gap] → `Bots` won't compile until `flyway:migrate` + `./mvnw generate-sources` run. Mitigate: this is an explicit apply-time step; the seed data never changes without a new migration.
- [Table-name collision] → a `bots` table might conflict with future bot feature tables. Mitigate: keep it a pure lookup (no FK to users needed for this feature).
- [Doc divergence] → implemented `users` differs from docs. Mitigate: `bots` is standalone and does not reference `users`, so no coupling risk.
- [Hydration between RoomService and BotEngine owner split] → the turn loop belongs to a different owner. Mitigate: scope this change to *resolution + depth passing*; orchestration is a follow-up change.

## Migration Plan

1. Add Flyway migration `V<ts>__create_bots_table.sql` (create + seed `bots`).
2. Run `./mvnw flyway:migrate` (DB must be up via `docker compose up -d`).
3. Run `./mvnw generate-sources` to generate the jOOQ `Bots` table + record.
4. Implement `BotDifficulty`, `BotConfigService`; wire `RoomService` to resolve config on room creation.
5. `./mvnw test` (unit: mapping + fallback; integration: create PvE/EVE room resolves depth and engine returns legal move).
Rollback: drop the migration version / revert the migration file; code is additive and self-contained in `game/bot`.

## Open Questions

- None blocking — all spec-level behaviors are resolved; the exact room-creation DTO shape will follow the existing (not-yet-built) `RoomService` pattern, but that does not change specs, approach, or task breakdown.