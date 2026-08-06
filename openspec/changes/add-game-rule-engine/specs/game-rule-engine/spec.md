# Frontend Game Rule Helper Specification

## ADDED Requirements

### Requirement: Board setup and terrain

The frontend SHALL expose the canonical nine-by-seven Jungle Chess terrain and sixteen-piece initial arrangement for board rendering.

#### Scenario: Initialize the board

- **WHEN** the game UI starts a new local game
- **THEN** it receives sixteen pieces and the configured rivers, dens, and traps

### Requirement: Temporary legal-move highlighting

The frontend SHALL return only locally legal destinations for a selected piece using the international movement, river, den, and friendly-occupancy rules.

#### Scenario: Block a river jump

- **WHEN** Lion or Tiger attempts to cross a river with a Rat in its path
- **THEN** the landing destination is not returned

### Requirement: Capture and trap rules

The frontend SHALL apply rank comparison, Rat–Elephant exceptions, water restrictions, and effective rank zero for a piece inside an opponent-owned trap.

#### Scenario: Capture a trapped piece

- **WHEN** an opponent piece is in the current side's trap
- **THEN** a lower-ranked current-side piece may capture it

### Requirement: Advisory game result

The frontend SHALL detect den entry and the absence of legal moves for local play. This result SHALL be replaced by backend-authoritative state in real-time modes.
