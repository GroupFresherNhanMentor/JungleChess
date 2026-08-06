# Implementation Tasks

## 1. Confirm frontend rule boundary and board contract

- [x] Keep work scoped to `GameRuleService` and shared frontend models.
- [x] Document the coordinate, terrain, and initial-piece arrangement in the service contract.
- **Verification:** No Room, WebSocket, Auth, Bot, Chat, or backend file is changed.

## 2. Harden board and movement helpers

- [x] Verify canonical tile ownership and initial setup.
- [x] Enforce orthogonal movement, own-den restriction, river access, and Lion/Tiger river jumps.
- **Verification:** Unit tests cover board setup, Rat river movement, and blocked/unblocked jumps.

## 3. Correct capture and trap rules

- [x] Implement effective ranks for both attacker and defender in traps.
- [x] Enforce Rat–Elephant and water-capture restrictions on all non-water terrain.
- **Verification:** Unit tests cover rank, trap, Rat–Elephant, and water edge cases.

## 4. Keep local result detection advisory

- [x] Detect den entry and no-legal-move outcome without owning server room state.
- [x] Return typed result values for UI consumers.
- **Verification:** Unit tests cover both result conditions.

## 5. Add regression tests and validate frontend build

- [x] Add focused tests for `GameRuleService` without modifying UI, animation, or Bot code.
- [x] Run the frontend test suite and production build.
- **Verification:** `npm test -- --watch=false` and `npm run build` pass.
