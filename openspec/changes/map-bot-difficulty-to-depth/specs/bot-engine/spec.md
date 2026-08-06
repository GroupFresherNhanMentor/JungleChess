## ADDED Requirements

### Requirement: Difficulty-driven search depth
The system SHALL configure the bot engine's search depth from a resolved `BotDifficulty`, so the difficulty a player selects directly controls the strength of the bot.

#### Scenario: Room creation passes difficulty depth to engine
- **WHEN** a room is created with mode `PVE` or `EVE` and a `botDifficulty`
- **THEN** the resolved `searchDepth` for that difficulty is passed to the bot engine when it computes moves, and the engine returns a legal move at that depth.

#### Scenario: Default difficulty when unspecified
- **WHEN** a room is created with mode `PVE` or `EVE` and no `botDifficulty` is supplied
- **THEN** the system uses a default difficulty and its corresponding search depth.
