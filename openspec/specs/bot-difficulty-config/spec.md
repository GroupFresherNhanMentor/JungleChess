# bot-difficulty-config Specification

## Purpose

Maps a player-facing bot difficulty (`EASY`, `MEDIUM`, `HARD`) to the search depth used by the bot engine, sourced from the `bots` table with a documented fallback, so room creation can resolve a concrete bot configuration.

## Requirements

### Requirement: Difficulty to search-depth mapping
The system SHALL resolve a `BotDifficulty` value (`EASY`, `MEDIUM`, `HARD`) to a concrete minimax `searchDepth` for the bot engine.

#### Scenario: EASY maps to depth 2
- **WHEN** a room requests difficulty `EASY`
- **THEN** the resolved search depth is `2`.

#### Scenario: MEDIUM maps to depth 4
- **WHEN** a room requests difficulty `MEDIUM`
- **THEN** the resolved search depth is `4`.

#### Scenario: HARD maps to depth 6
- **WHEN** a room requests difficulty `HARD`
- **THEN** the resolved search depth is `6`.

### Requirement: Bot configuration lookup
The system SHALL provide a single lookup that returns a bot's configuration for a requested difficulty, preferring the `bots` table row matching that difficulty and falling back to the default mapping when no row exists.

#### Scenario: Bot row exists for difficulty
- **WHEN** a `bots` table row exists for the requested difficulty
- **THEN** the lookup returns the `searchDepth` from that row.

#### Scenario: No bot row exists for difficulty
- **WHEN** no `bots` table row exists for the requested difficulty
- **THEN** the lookup returns the default depth for that difficulty (EASY=2, MEDIUM=4, HARD=6) without error.
