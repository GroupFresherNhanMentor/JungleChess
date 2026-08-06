# Design: Frontend Game Rule Helper

## Ownership and Boundary

The implementation belongs to the Angular frontend and stays in `GameRuleService`. It provides temporary local validation for the board UI; it never owns room state, WebSocket broadcasts, authentication, chat, Bot minimax, or persistence.

The Room/WebSocket backend is the final authority. The frontend must replace or reconcile its local state with server state when that integration exists.

## Service Responsibilities

```text
GameRuleService
  - getTileInfo / isWaterTile
  - getInitialPieces
  - getPieceAt
  - getValidMoves
  - canCapture
  - checkWinCondition
```

The service uses `Piece`, `Position`, `Tile`, and `Move` from `fe/src/app/core/models/game.models.ts`. It returns destinations for board highlighting and does not mutate the input piece list.

## Canonical Frontend Layout

- Board coordinates are zero-based `{ col: 0..6, row: 0..8 }`.
- Red (side `1`) begins at the top and owns the top den/traps; Blue (side `0`) begins at the bottom and owns the bottom den/traps.
- Rivers occupy rows `3..5`, columns `1..2` and `4..5`.
- The initial layout follows the international 16-piece arrangement already rendered by the board.

## Rules

- Pieces move one orthogonal cell, except Lion/Tiger jump across a river.
- A piece cannot land on a friendly piece or its own den.
- Only Rat enters river cells. A Rat in water may capture only a Rat in water; it cannot capture a land piece. Lion/Tiger jumps are blocked by any Rat in crossed river cells.
- Rank normally decides capture. Rat captures Elephant on land; Elephant never captures Rat.
- A piece in an opponent-owned trap has effective rank zero and cannot capture another piece.
- Local result detection reports den entry or the absence of a legal move; it is advisory until confirmed by backend state.

## Verification

Use Angular's unit-test runner to cover terrain/setup, river entry, Lion/Tiger jumps, capture/trap exceptions, and local win detection. No browser, backend, database, or WebSocket is needed.
