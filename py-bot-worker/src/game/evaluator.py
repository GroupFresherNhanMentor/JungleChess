from typing import Optional, Dict, List
from .model import Board, Piece, PieceType, Position, Side

class BoardEvaluator:
    WIN_SCORE = 100000
    LOSS_SCORE = -100000

    PIECE_VALUES = {
        PieceType.RAT: 100,
        PieceType.CAT: 200,
        PieceType.DOG: 300,
        PieceType.WOLF: 400,
        PieceType.LEOPARD: 500,
        PieceType.TIGER: 600,
        PieceType.LION: 700,
        PieceType.ELEPHANT: 800,
    }

    P1_TRAPS = [(0, 2), (0, 4), (1, 3)]
    P2_TRAPS = [(8, 2), (8, 4), (7, 3)]

    PST_DEFAULT = [
        [ 0,  5, 10, 15, 10,  5,  0 ],
        [ 5, 10, 15, 20, 15, 10,  5 ],
        [ 10, 15, 20, 25, 20, 15, 10 ],
        [ 15, 20, 25, 30, 25, 20, 15 ],
        [ 20, 25, 30, 35, 30, 25, 20 ],
        [ 25, 30, 35, 40, 35, 30, 25 ],
        [ 30, 35, 40, 50, 40, 35, 30 ],
        [ 40, 50, 60, 75, 60, 50, 40 ],
        [ 50, 65, 80, 100, 80, 65, 50 ]
    ]

    PST_RAT = [
        [ 0,  5, 10, 15, 10,  5,  0 ],
        [ 5, 10, 15, 20, 15, 10,  5 ],
        [ 10, 20, 25, 25, 25, 20, 10 ],
        [ 15, 35, 40, 30, 40, 35, 15 ],
        [ 20, 40, 45, 35, 45, 40, 20 ],
        [ 25, 35, 40, 35, 40, 35, 25 ],
        [ 30, 40, 45, 55, 45, 40, 30 ],
        [ 40, 55, 65, 80, 65, 55, 40 ],
        [ 50, 70, 85, 100, 85, 70, 50 ]
    ]

    PST_JUMPER = [
        [ 0,  5, 10, 15, 10,  5,  0 ],
        [ 5, 10, 15, 20, 15, 10,  5 ],
        [ 15, 30, 35, 30, 35, 30, 15 ],
        [ 15, 20, 25, 30, 25, 20, 15 ],
        [ 20, 25, 30, 35, 30, 25, 20 ],
        [ 25, 30, 35, 40, 35, 30, 25 ],
        [ 20, 35, 40, 55, 40, 35, 20 ],
        [ 40, 50, 65, 80, 65, 50, 40 ],
        [ 50, 65, 85, 100, 85, 65, 50 ]
    ]

    def evaluate(self, board: Board, side: Side) -> int:
        opponent = side.opposite
        target_den = Position(8, 3) if side == Side.PLAYER_1 else Position(0, 3)
        own_den = Position(0, 3) if side == Side.PLAYER_1 else Position(8, 3)

        target_den_piece = board.get_piece(target_den.row, target_den.col)
        if target_den_piece is not None and target_den_piece.side == side:
            return self.WIN_SCORE

        own_den_piece = board.get_piece(own_den.row, own_den.col)
        if own_den_piece is not None and own_den_piece.side == opponent:
            return self.LOSS_SCORE

        score = 0
        min_opponent_dist_to_own_den = 999

        for r in range(Board.ROWS):
            for c in range(Board.COLS):
                piece = board.get_piece(r, c)
                if piece is None:
                    continue

                piece_side = piece.side
                piece_opp = piece_side.opposite
                piece_val = self.PIECE_VALUES.get(piece.type, 0)
                positional_bonus = self._get_pst_value(piece, r, c)

                trap_penalty = 0
                if Board.is_trap(r, c, piece_opp):
                    trap_penalty = piece_val

                trap_control_bonus = 0
                if self._is_adjacent_to_enemy_trap(r, c, piece_opp):
                    trap_control_bonus = 40

                nemesis_penalty = 0
                if piece.type == PieceType.ELEPHANT:
                    enemy_rat_pos = self._find_piece(board, piece_opp, PieceType.RAT)
                    if enemy_rat_pos is not None:
                        dist_to_rat = abs(r - enemy_rat_pos.row) + abs(c - enemy_rat_pos.col)
                        if dist_to_rat == 1 and not Board.is_river(enemy_rat_pos.row, enemy_rat_pos.col):
                            nemesis_penalty = 800
                        elif dist_to_rat == 2:
                            nemesis_penalty = 300

                total_piece_score = piece_val + positional_bonus + trap_control_bonus - trap_penalty - nemesis_penalty

                if piece_side == side:
                    score += total_piece_score
                else:
                    score -= total_piece_score
                    dist_to_our_den = abs(r - own_den.row) + abs(c - own_den.col)
                    if dist_to_our_den < min_opponent_dist_to_own_den:
                        min_opponent_dist_to_own_den = dist_to_our_den

        score += self._evaluate_home_trap_ambush(board, side)
        score -= self._evaluate_home_trap_ambush(board, opponent)

        if min_opponent_dist_to_own_den <= 3:
            is_defended = self._is_home_den_defended(board, side)
            if min_opponent_dist_to_own_den == 1:
                score -= 500 if is_defended else 2500
            elif min_opponent_dist_to_own_den == 2:
                score -= 250 if is_defended else 800
            elif min_opponent_dist_to_own_den == 3:
                score -= 100 if is_defended else 300
            if is_defended:
                score += 200

        return score

    def _evaluate_home_trap_ambush(self, board: Board, side: Side) -> int:
        bonus = 0
        home_traps = self.P1_TRAPS if side == Side.PLAYER_1 else self.P2_TRAPS

        for tr, tc in home_traps:
            trap_piece = board.get_piece(tr, tc)
            if trap_piece is not None and trap_piece.side == side:
                bonus -= 50
            elif trap_piece is not None and trap_piece.side == side.opposite:
                if self._has_friendly_adjacent(board, tr, tc, side):
                    bonus += 300
            else:
                if self._has_friendly_adjacent(board, tr, tc, side):
                    bonus += 150
        return bonus

    def _has_friendly_adjacent(self, board: Board, row: int, col: int, friendly_side: Side) -> bool:
        for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            r, c = row + dr, col + dc
            if 0 <= r < Board.ROWS and 0 <= c < Board.COLS:
                p = board.get_piece(r, c)
                if p is not None and p.side == friendly_side:
                    return True
        return False

    def _get_pst_value(self, piece: Piece, row: int, col: int) -> int:
        r = row if piece.side == Side.PLAYER_1 else (8 - row)
        if piece.type == PieceType.RAT:
            return self.PST_RAT[r][col]
        elif piece.type in (PieceType.LION, PieceType.TIGER):
            return self.PST_JUMPER[r][col]
        else:
            return self.PST_DEFAULT[r][col]

    def _is_adjacent_to_enemy_trap(self, row: int, col: int, piece_opp: Side) -> bool:
        enemy_traps = self.P2_TRAPS if piece_opp == Side.PLAYER_2 else self.P1_TRAPS
        for tr, tc in enemy_traps:
            if abs(row - tr) + abs(col - tc) == 1:
                return True
        return False

    def _is_home_den_defended(self, board: Board, side: Side) -> bool:
        defender_squares = (
            [(0, 2), (0, 4), (1, 3), (1, 2), (1, 4), (2, 3)]
            if side == Side.PLAYER_1
            else [(8, 2), (8, 4), (7, 3), (7, 2), (7, 4), (6, 3)]
        )
        for r, c in defender_squares:
            p = board.get_piece(r, c)
            if p is not None and p.side == side:
                return True
        return False

    def _find_piece(self, board: Board, side: Side, ptype: PieceType) -> Optional[Position]:
        for r in range(Board.ROWS):
            for c in range(Board.COLS):
                p = board.get_piece(r, c)
                if p is not None and p.side == side and p.type == ptype:
                    return Position(r, c)
        return None
