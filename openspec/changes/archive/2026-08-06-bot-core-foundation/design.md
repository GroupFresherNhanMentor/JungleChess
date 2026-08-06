## Context

See `proposal.md` for background and motivation. 
The Jungle Chess backend runs on Java / Spring Boot 3+. The game domain logic (`game/`) must remain pure Java without WebSockets, HTTP controllers, or database persistence dependencies so it can be unit-tested cleanly and invoked by both `RoomService` and `BotEngine`.

## Goals / Non-Goals

**Goals:**
- Provide zero-garbage-collection board mutation (`makeMove` / `undoMove` - Option A pattern) for high-frequency Minimax search trees.
- Define shared, strongly-typed domain models (`Side`, `PieceType`, `Piece`, `Position`, `Move`, `Board`).
- Implement full move generation rules in `GameRuleEngine` according to international Jungle Chess standards (`docs/SRS_Co_Thu_Online.md` Section 3).

**Non-Goals:**
- WebSocket STOMP session coordination (handled by `RoomService`).
- Minimax search tree evaluation & depth heuristic tuning (handled in Phase 2 & 3).

## Decisions

### Decision 1: Option A — In-Place Board Mutation (`makeMove`/`undoMove`)
- **Rationale**: Minimax tree traversal at depth 4 to 6 evaluates 10,000 to 500,000 board states per move. Allocating new `Board` instances or matrix arrays for each branch causes severe JVM Garbage Collection pauses. In-place mutation with stack-based move backtracking achieves sub-millisecond per-node processing time.
- **Alternatives Considered**: 
  - *Option B (Immutable Defensive Copy)*: Clean functional code, but benchmarked heavy GC overhead during deep searches.

### Decision 2: Shared Domain Package Layout (`be/game/`)
- **Package Structure**:
  - `fpt.qn.junglechess.game.model`: Data structures (`Side`, `PieceType`, `Piece`, `Position`, `Move`, `Board`).
  - `fpt.qn.junglechess.game.rule`: `GameRuleEngine` interface and default implementation.
  - `fpt.qn.junglechess.game.bot`: `BotEngine` interface.
- **Rationale**: Maintains strict separation of concerns; keeps rule engine and domain models isolated from web/persistence layers.

### Decision 3: 0-Indexed Coordinate Grid (`[row, col]`)
- **Grid Mapping**: 9 rows (0 to 8) x 7 columns (0 to 6) matching `docs/API_Spec_Co_Thu_Online.md`.
  - P1 Den: `[0, 3]`, P2 Den: `[8, 3]`.
  - P1 Traps: `[0, 2]`, `[0, 4]`, `[1, 3]`.
  - P2 Traps: `[8, 2]`, `[8, 4]`, `[7, 3]`.
  - Rivers: Left `[3..5, 1..2]`, Right `[3..5, 4..5]`.

## Risks / Trade-offs

- **[Risk] State corruption during backtracking if `undoMove` misses state attributes** $\to$ **Mitigation**: Move data structure encapsulates `capturedPiece` and previous `specialEvent`. Comprehensive unit tests will verify board equality before `makeMove` and after `undoMove`.
- **[Risk] Thread safety when sharing board instances** $\to$ **Mitigation**: `RoomService` creates a defensive clone of `Board` when passing it to `BotEngine.nextMove()`. `BotEngine` operates exclusively on its local board clone during Minimax search.
