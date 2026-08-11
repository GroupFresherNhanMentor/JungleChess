import random
from enum import Enum
from typing import Optional, List, Tuple

class Side(Enum):
    PLAYER_1 = "PLAYER_1"
    PLAYER_2 = "PLAYER_2"

    @property
    def opposite(self) -> "Side":
        return Side.PLAYER_2 if self == Side.PLAYER_1 else Side.PLAYER_1

class PieceType(Enum):
    RAT = (1, "RAT")
    CAT = (2, "CAT")
    DOG = (3, "DOG")
    WOLF = (4, "WOLF")
    LEOPARD = (5, "LEOPARD")
    TIGER = (6, "TIGER")
    LION = (7, "LION")
    ELEPHANT = (8, "ELEPHANT")

    def __init__(self, rank: int, code: str):
        self.rank = rank
        self.code = code

class Piece:
    __slots__ = ('side', 'type')
    def __init__(self, side: Side, piece_type: PieceType):
        self.side = side
        self.type = piece_type

    def __repr__(self):
        return f"{self.side.name}_{self.type.name}"

class Position:
    __slots__ = ('row', 'col')
    def __init__(self, row: int, col: int):
        self.row = row
        self.col = col

    def __eq__(self, other):
        return isinstance(other, Position) and self.row == other.row and self.col == other.col

    def __hash__(self):
        return hash((self.row, self.col))

    def __repr__(self):
        return f"({self.row},{self.col})"

class Move:
    __slots__ = ('from_pos', 'to_pos', 'moved_piece', 'captured_piece')
    def __init__(self, from_pos: Position, to_pos: Position, moved_piece: Piece, captured_piece: Optional[Piece] = None):
        self.from_pos = from_pos
        self.to_pos = to_pos
        self.moved_piece = moved_piece
        self.captured_piece = captured_piece

    def __eq__(self, other):
        return (isinstance(other, Move) and 
                self.from_pos == other.from_pos and 
                self.to_pos == other.to_pos)

    def __hash__(self):
        return hash((self.from_pos.row, self.from_pos.col, self.to_pos.row, self.to_pos.col))

    def __repr__(self):
        return f"Move({self.from_pos}->{self.to_pos}, {self.moved_piece}, cap={self.captured_piece})"

class ZobristTable:
    # 9 rows * 7 cols * 2 sides * 8 piece types
    TABLE = [[[[0 for _ in range(8)] for _ in range(2)] for _ in range(7)] for _ in range(9)]
    BLACK_TURN_KEY = 0

    @classmethod
    def _init(cls):
        rng = random.Random(42)
        for r in range(9):
            for c in range(7):
                for s in range(2):
                    for p in range(8):
                        cls.TABLE[r][c][s][p] = rng.getrandbits(64)
        cls.BLACK_TURN_KEY = rng.getrandbits(64)

ZobristTable._init()

class Board:
    ROWS = 9
    COLS = 7

    RIVER_SQUARES = {
        (3, 1), (3, 2), (4, 1), (4, 2), (5, 1), (5, 2),
        (3, 4), (3, 5), (4, 4), (4, 5), (5, 4), (5, 5)
    }

    P1_TRAPS = {(0, 2), (0, 4), (1, 3)}
    P2_TRAPS = {(8, 2), (8, 4), (7, 3)}

    P1_DEN = (0, 3)
    P2_DEN = (8, 3)

    def __init__(self):
        self.grid: List[List[Optional[Piece]]] = [[None for _ in range(self.COLS)] for _ in range(self.ROWS)]
        self.zobrist_hash = 0

    @classmethod
    def create_initial(cls) -> "Board":
        board = cls()
        # PLAYER_1 pieces (Top)
        board.set_piece(0, 0, Piece(Side.PLAYER_1, PieceType.LION))
        board.set_piece(0, 6, Piece(Side.PLAYER_1, PieceType.TIGER))
        board.set_piece(1, 1, Piece(Side.PLAYER_1, PieceType.DOG))
        board.set_piece(1, 5, Piece(Side.PLAYER_1, PieceType.CAT))
        board.set_piece(2, 0, Piece(Side.PLAYER_1, PieceType.RAT))
        board.set_piece(2, 2, Piece(Side.PLAYER_1, PieceType.LEOPARD))
        board.set_piece(2, 4, Piece(Side.PLAYER_1, PieceType.WOLF))
        board.set_piece(2, 6, Piece(Side.PLAYER_1, PieceType.ELEPHANT))

        # PLAYER_2 pieces (Bottom)
        board.set_piece(8, 6, Piece(Side.PLAYER_2, PieceType.LION))
        board.set_piece(8, 0, Piece(Side.PLAYER_2, PieceType.TIGER))
        board.set_piece(7, 5, Piece(Side.PLAYER_2, PieceType.DOG))
        board.set_piece(7, 1, Piece(Side.PLAYER_2, PieceType.CAT))
        board.set_piece(6, 6, Piece(Side.PLAYER_2, PieceType.RAT))
        board.set_piece(6, 4, Piece(Side.PLAYER_2, PieceType.LEOPARD))
        board.set_piece(6, 2, Piece(Side.PLAYER_2, PieceType.WOLF))
        board.set_piece(6, 0, Piece(Side.PLAYER_2, PieceType.ELEPHANT))
        return board

    def get_piece(self, row: int, col: int) -> Optional[Piece]:
        return self.grid[row][col]

    def set_piece(self, row: int, col: int, piece: Optional[Piece]):
        old = self.grid[row][col]
        if old is not None:
            side_idx = 0 if old.side == Side.PLAYER_1 else 1
            type_idx = old.type.rank - 1
            self.zobrist_hash ^= ZobristTable.TABLE[row][col][side_idx][type_idx]
        self.grid[row][col] = piece
        if piece is not None:
            side_idx = 0 if piece.side == Side.PLAYER_1 else 1
            type_idx = piece.type.rank - 1
            self.zobrist_hash ^= ZobristTable.TABLE[row][col][side_idx][type_idx]

    def make_move(self, move: Move):
        self.set_piece(move.from_pos.row, move.from_pos.col, None)
        self.set_piece(move.to_pos.row, move.to_pos.col, move.moved_piece)

    def undo_move(self, move: Move):
        self.set_piece(move.to_pos.row, move.to_pos.col, move.captured_piece)
        self.set_piece(move.from_pos.row, move.from_pos.col, move.moved_piece)

    def clone_board(self) -> "Board":
        new_board = Board()
        for r in range(self.ROWS):
            for c in range(self.COLS):
                p = self.grid[r][c]
                if p is not None:
                    new_board.grid[r][c] = Piece(p.side, p.type)
        new_board.zobrist_hash = self.zobrist_hash
        return new_board

    def recompute_zobrist(self):
        self.zobrist_hash = 0
        for r in range(self.ROWS):
            for c in range(self.COLS):
                p = self.grid[r][c]
                if p is not None:
                    side_idx = 0 if p.side == Side.PLAYER_1 else 1
                    type_idx = p.type.rank - 1
                    self.zobrist_hash ^= ZobristTable.TABLE[r][c][side_idx][type_idx]

    @classmethod
    def is_river(cls, row: int, col: int) -> bool:
        return (row, col) in cls.RIVER_SQUARES

    @classmethod
    def is_trap(cls, row: int, col: int, piece_side: Side) -> bool:
        if piece_side == Side.PLAYER_1:
            return (row, col) in cls.P2_TRAPS
        else:
            return (row, col) in cls.P1_TRAPS

    @classmethod
    def is_den(cls, row: int, col: int, den_owner: Side) -> bool:
        if den_owner == Side.PLAYER_1:
            return (row, col) == cls.P1_DEN
        else:
            return (row, col) == cls.P2_DEN

    @staticmethod
    def compute_zobrist_key(board_hash: int, current_turn: Side) -> int:
        key = board_hash
        if current_turn == Side.PLAYER_2:
            key ^= ZobristTable.BLACK_TURN_KEY
        return key
