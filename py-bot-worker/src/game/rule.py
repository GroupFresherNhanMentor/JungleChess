from typing import List
from .model import Board, Move, Piece, PieceType, Position, Side

class DefaultGameRuleEngine:

    DIRECTIONS = [(-1, 0), (1, 0), (0, -1), (0, 1)]

    def get_valid_moves(self, board: Board, side: Side) -> List[Move]:
        valid_moves: List[Move] = []
        for r in range(Board.ROWS):
            for c in range(Board.COLS):
                piece = board.get_piece(r, c)
                if piece is not None and piece.side == side:
                    from_pos = Position(r, c)
                    for dr, dc in self.DIRECTIONS:
                        to_r, to_c = r + dr, c + dc
                        if not self._is_within_board(to_r, to_c):
                            continue

                        # Cannot step into own den
                        if Board.is_den(to_r, to_c, side):
                            continue

                        # Check river rule
                        if Board.is_river(to_r, to_c):
                            if piece.type == PieceType.RAT:
                                target_piece = board.get_piece(to_r, to_c)
                                if self._can_capture(piece, from_pos, target_piece, Position(to_r, to_c), board):
                                    valid_moves.append(Move(from_pos, Position(to_r, to_c), piece, target_piece))
                            elif piece.type in (PieceType.LION, PieceType.TIGER):
                                land_pos = self._jump_over_river(r, c, dr, dc, board)
                                if land_pos is not None:
                                    if not Board.is_den(land_pos.row, land_pos.col, side):
                                        target_piece = board.get_piece(land_pos.row, land_pos.col)
                                        if self._can_capture(piece, from_pos, target_piece, land_pos, board):
                                            valid_moves.append(Move(from_pos, land_pos, piece, target_piece))
                        else:
                            target_piece = board.get_piece(to_r, to_c)
                            if self._can_capture(piece, from_pos, target_piece, Position(to_r, to_c), board):
                                valid_moves.append(Move(from_pos, Position(to_r, to_c), piece, target_piece))

        return valid_moves

    def _can_capture(self, attacker: Piece, from_pos: Position, defender: Piece, to_pos: Position, board: Board) -> bool:
        if defender is None:
            return True
        if defender.side == attacker.side:
            return False

        # If defender is in attacker's home traps (i.e. enemy in attacker trap), defender rank = 0
        if Board.is_trap(to_pos.row, to_pos.col, defender.side):
            return True

        # Rat in river cannot capture piece on land
        if Board.is_river(from_pos.row, from_pos.col) and not Board.is_river(to_pos.row, to_pos.col):
            return False

        # Special Rat vs Elephant rule
        if attacker.type == PieceType.RAT and defender.type == PieceType.ELEPHANT:
            return True
        if attacker.type == PieceType.ELEPHANT and defender.type == PieceType.RAT:
            return False

        return attacker.type.rank >= defender.type.rank

    def _jump_over_river(self, row: int, col: int, dr: int, dc: int, board: Board) -> Position:
        curr_r, curr_c = row + dr, col + dc
        while self._is_within_board(curr_r, curr_c) and Board.is_river(curr_r, curr_c):
            if board.get_piece(curr_r, curr_c) is not None: # Rat blocking jump
                return None
            curr_r += dr
            curr_c += dc

        if self._is_within_board(curr_r, curr_c):
            return Position(curr_r, curr_c)
        return None

    @staticmethod
    def _is_within_board(r: int, c: int) -> bool:
        return 0 <= r < Board.ROWS and 0 <= c < Board.COLS
