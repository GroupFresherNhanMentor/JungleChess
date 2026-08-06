# Proposal: Frontend Game Rule Helper

## Problem

The Angular board needs one reliable source for initial setup and temporary legal-move highlights. The existing helper contains the basic board rules but lacks test coverage and has edge cases around traps and water captures.

## Scope

This change completes the frontend `GameRuleService`: canonical board terrain/setup, temporary move generation, capture checks, local game-result detection, and unit tests.

It does not implement backend rule validation, WebSocket/authentication, room lifecycle, Bot search, chat, persistence, board assets, or animation/sound.

## Proposed Approach

- Keep the work inside `fe/src/app/core/services/game-rule.service.ts` and its shared game models.
- Use the service only for frontend interaction and highlight; the backend remains authoritative once the real-time flow is connected.
- Correct international-rule edge cases for traps, river movement, Lion/Tiger jumps, and Rat–Elephant capture restrictions.
- Add isolated Angular unit tests that document the rule behaviour.
