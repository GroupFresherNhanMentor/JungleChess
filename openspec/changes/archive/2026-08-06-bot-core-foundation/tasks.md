## 1. Domain Models Setup (`be/game/model/`)

- [x] 1.1 Create `Side` enum (`PLAYER_1`, `PLAYER_2`) and `PieceType` enum (`RAT`..`ELEPHANT`) with ranks 1 to 8.
- [x] 1.2 Create immutable `Position` (row 0..8, col 0..6) and `Piece` record/class (`Side`, `PieceType`).
- [x] 1.3 Create `SpecialEvent` enum (`RIVER_JUMP`, `TRAP_NEUTRALIZED`) and `Move` record (`from`, `to`, `movedPiece`, `capturedPiece`, `specialEvent`).
- [x] 1.4 Implement `Board` matrix class with coordinate terrain helper methods (`isTrap`, `isRiver`, `isDen`).

## 2. In-Place Board Mutation (Option A Pattern)

- [x] 2.1 Implement `Board.makeMove(Move move)` updating piece matrix and grid position states.
- [x] 2.2 Implement `Board.undoMove(Move move)` restoring previous grid state and captured piece.
- [x] 2.3 Write JUnit 5 unit test `BoardMutationTest` verifying board equality before `makeMove` and after `undoMove`.

## 3. Game Rule Engine (`be/game/rule/`)

- [x] 3.1 Implement `GameRuleEngine` interface with `getValidMoves(Board board, Side side)` and `validateMove(Board board, Move move)`.
- [x] 3.2 Implement orthogonal 1-step move generation and boundary checks.
- [x] 3.3 Implement terrain constraints: river entry restriction for non-Rat pieces, Den entry restrictions for own side.
- [x] 3.4 Implement Tiger/Lion river jump move generation and check for Rat block along river jump paths.
- [x] 3.5 Implement capture rules: rank hierarchy checks, Rat-vs-Elephant exception, and Trap cell rank neutralization (rank=0).
- [x] 3.6 Write JUnit 5 test suite `GameRuleEngineTest` covering all international rule edge cases.

## 4. Bot Engine Interface (`be/game/bot/`)

- [x] 4.1 Define `BotEngine` interface with `nextMove(Board board, Side side, int depth)` method signature.
