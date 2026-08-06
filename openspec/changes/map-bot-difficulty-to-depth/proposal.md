## Why

The bot AI engine (`BotEngine.nextMove`) currently takes a raw `depth` parameter, but the game contract exposes `botDifficulty` (`EASY`/`MEDIUM`/`HARD`) to players. There is no wiring between the difficulty a player selects and the search depth actually used, so the three difficulty levels in the API spec (`docs/API_Spec_Co_Thu_Online.md` §3.3.1) have no effect today. Room creation cannot yet configure a bot, which blocks the PvE/EvE gameplay flows.

## What Changes

- Introduce a `BotDifficulty` enum (`EASY`, `MEDIUM`, `HARD`) and a mapping from difficulty → `searchDepth` (`EASY=2`, `MEDIUM=4`, `HARD=6`).
- Add a `BotConfigService` that resolves a bot's difficulty and `searchDepth` — sourced from the `bots` table (seed data per `docs/DB_Design_Co_Thu_Online.md` §3.3) with a sensible in-code fallback when no row exists.
- On room creation (mode `PVE`/`EVE`), look up the bot config for the requested `botDifficulty` and pass the resolved `searchDepth` into `BotEngine.nextMove`.
- Wire difficulty into the room-creation contract so `botDifficulty` from `/app/room/create` is accepted and honored.
- **BREAKING**: none to the WebSocket contract — `botDifficulty` was already part of the documented payload; this change makes the backend actually honor it.

## Capabilities

### New Capabilities

- `bot-difficulty-config`: Maps the `botDifficulty` contract value to a concrete search depth for the bot engine, sourced from the `bots` table with a fallback, and exposes a single entry point for room creation to resolve a bot's configuration.

### Modified Capabilities

- `bot-engine`: The `BotEngine.nextMove(Board, Side, int)` contract is unchanged, but the requirement that difficulty drives search depth is added: the bot must be configurable by difficulty, and room creation must resolve that configuration before invoking the engine.

## Impact

- **Backend code**: new `be/game/bot/BotDifficulty.java`, `be/game/bot/config/BotConfigService` (reads `bots` table via jOOQ, applies `EASY=2`/`MEDIUM=4`/`HARD=6`), and `RoomService`/room-creation wiring (mode `PVE`/`EVE`) to resolve config and pass depth.
- **Database**: relies on the `bots` table schema already defined in `docs/DB_Design_Co_Thu_Online.md` (seed: EASY depth 2, MEDIUM depth 4, HARD depth 6). May add a Flyway seed migration if no seed exists yet.
- **API contract**: consumes `botDifficulty` already specified in `docs/API_Spec_Co_Thu_Online.md` §3.3.1; no field changes.
- **Tests**: unit tests for difficulty mapping + fallback; integration test proving room creation resolves depth and the bot plays a legal move at each difficulty.
