## Purpose

Provides high-performance board state mutation, game model representations, and rule-compliant valid move generation for the Jungle Chess Bot AI engine.

## ADDED Requirements

### Requirement: 9x7 Board Grid and Piece Representation
The system SHALL represent the Jungle Chess board as a 9-row by 7-column grid with 0-indexed coordinates (`row` 0 to 8, `col` 0 to 6) supporting piece types (RAT to ELEPHANT) and sides (PLAYER_1, PLAYER_2).

#### Scenario: Valid board initialization
- **WHEN** the board is initialized for a new match
- **THEN** pieces are placed at standard international Jungle Chess starting positions and terrain markers (Dens, Traps, Rivers) are mapped to exact grid coordinates.

### Requirement: In-Place Board State Execution and Undo (Option A)
The system SHALL execute moves on the board in-place via `makeMove` and revert them accurately via `undoMove` without instantiating new board matrix objects.

#### Scenario: Make and undo move state consistency
- **WHEN** a valid move is executed on the board via `makeMove` and subsequently reverted via `undoMove`
- **THEN** the board state, piece positions, and captured piece attributes match the exact state prior to `makeMove`.

### Requirement: Valid Move Generation
The system SHALL generate all legal moves for a given side according to international Jungle Chess rules, including piece ranks, river jumps for Tiger/Lion, and trap cell rank neutralizations.

#### Scenario: River jump generation for Tiger and Lion
- **WHEN** a Tiger or Lion is adjacent to a river cell and no Rat occupies the jump trajectory in the river
- **THEN** the system generates a valid river jump move landing directly on the opposite bank.

#### Scenario: Trap cell rank neutralization
- **WHEN** an opponent piece enters a trap cell belonging to the friendly side
- **THEN** any friendly piece can capture the trapped opponent piece regardless of standard piece rank hierarchy.
