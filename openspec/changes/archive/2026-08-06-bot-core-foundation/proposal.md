## Why

The Jungle Chess backend requires an AI Bot engine for PvE (Player vs Bot) and EvE (Bot vs Spectator) game modes as specified in SRS (`BOT-01` to `BOT-06`) and Architecture Section 4.5. 
To achieve sub-second execution for Minimax search with Alpha-Beta pruning, the bot AI requires a high-performance core foundation: shared domain models (`Board`, `Piece`, `Position`, `Move`), in-place move execution & backtracking (`makeMove`/`undoMove` Option A pattern) to avoid Garbage Collection memory overhead, and legal move generation via `GameRuleEngine`.

## What Changes

- **Shared Game Domain Models (`be/game/model/`)**: Define 9x7 board grid representation, piece types (`RAT`..`ELEPHANT`), sides (`PLAYER_1`, `PLAYER_2`), move models, and terrain coordinate constants (Dens, Traps, Rivers).
- **In-Place Board State Management (Option A Pattern)**: Implement `Board` methods `makeMove(Move move)` and `undoMove(Move move)` so Minimax search tree traversal performs zero-allocation state changes during evaluation.
- **Move Generation Interface (`GameRuleEngine.getValidMoves`)**: Provide a pure rule-checking component for candidate move generation considering piece ranks, river jumps for Tiger/Lion, trap neutralizations, and Den entry rules.
- **Bot Engine Core Interface (`BotEngine`)**: Define entry point `BotEngine.nextMove(Board board, Side side, int depth)` to decouple AI computation from WebSocket session management.

## Capabilities

### New Capabilities
- `bot-engine`: Core foundation for Jungle Chess Bot AI engine, including 9x7 board representation, in-place move/undo execution, and legal move generation interface.

### Modified Capabilities
*(None — no existing requirement specifications are modified)*

## Impact

- **Backend (`be/`)**: Adds new package structure `fpt.qn.junglechess.game.model`, `fpt.qn.junglechess.game.rule`, and `fpt.qn.junglechess.game.bot`.
- **API & Protocol**: Aligns with coordinate system `[row, col]` (0-index: 9 rows x 7 cols) and `pieceCode` definitions in `docs/API_Spec_Co_Thu_Online.md`.
- **Dependencies**: Pure Java domain logic; no external database or network dependencies.
