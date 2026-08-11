import time
import logging
from enum import Enum
from typing import Optional, Dict, List, Tuple
from .model import Board, Move, Piece, PieceType, Position, Side
from .rule import DefaultGameRuleEngine
from .evaluator import BoardEvaluator

logger = logging.getLogger("AlphaBetaBotEngine")

class TtFlag(Enum):
    EXACT = 0
    LOWER_BOUND = 1
    UPPER_BOUND = 2

class TtEntry:
    __slots__ = ('key', 'depth', 'score', 'flag', 'best_move')
    def __init__(self, key: int, depth: int, score: int, flag: TtFlag, best_move: Optional[Move]):
        self.key = key
        self.depth = depth
        self.score = score
        self.flag = flag
        self.best_move = best_move

class BotContext:
    def __init__(self):
        self.tt: Dict[int, TtEntry] = {}
        self.position_history: Dict[int, int] = {}
        self.killer_moves: Dict[int, List[Optional[Move]]] = {}
        self.history_scores: Dict[Tuple[int, int, int, int], int] = {}

    def record_position(self, key: int):
        self.position_history[key] = self.position_history.get(key, 0) + 1

    def push_position(self, key: int):
        self.position_history[key] = self.position_history.get(key, 0) + 1

    def pop_position(self, key: int):
        if key in self.position_history:
            self.position_history[key] -= 1
            if self.position_history[key] <= 0:
                del self.position_history[key]

    def get_position_count(self, key: int) -> int:
        return self.position_history.get(key, 0)

    def get_tt_entry(self, key: int) -> Optional[TtEntry]:
        return self.tt.get(key)

    def store_tt_entry(self, key: int, entry: TtEntry):
        self.tt[key] = entry

    def store_killer_move(self, ply: int, move: Move):
        if ply not in self.killer_moves:
            self.killer_moves[ply] = [None, None]
        killers = self.killer_moves[ply]
        if killers[0] != move:
            killers[1] = killers[0]
            killers[0] = move

    def get_killer_moves(self, ply: int) -> List[Optional[Move]]:
        return self.killer_moves.get(ply, [None, None])

    def add_history_score(self, move: Move, depth: int):
        m_key = (move.from_pos.row, move.from_pos.col, move.to_pos.row, move.to_pos.col)
        self.history_scores[m_key] = self.history_scores.get(m_key, 0) + (depth * depth)

    def get_history_score(self, move: Move) -> int:
        m_key = (move.from_pos.row, move.from_pos.col, move.to_pos.row, move.to_pos.col)
        return self.history_scores.get(m_key, 0)

    def age_history_scores(self):
        for k in list(self.history_scores.keys()):
            self.history_scores[k] //= 2


class AlphaBetaBotEngine:
    P1_DEN_ROW, P1_DEN_COL = 0, 3
    P2_DEN_ROW, P2_DEN_COL = 8, 3
    MAX_QS_DEPTH = 4

    def __init__(self, rule_engine: Optional[DefaultGameRuleEngine] = None, evaluator: Optional[BoardEvaluator] = None):
        self.rule_engine = rule_engine or DefaultGameRuleEngine()
        self.evaluator = evaluator or BoardEvaluator()

    def next_move(self, board: Board, side: Side, max_depth: int, timeout_ms: int = 2500, context: Optional[BotContext] = None) -> Optional[Move]:
        if context is None:
            context = BotContext()
        context.age_history_scores()

        working_board = board.clone_board()
        valid_moves = self.rule_engine.get_valid_moves(working_board, side)
        if not valid_moves:
            return None

        start_time_ns = time.time_ns()
        deadline_ns = start_time_ns + int(timeout_ms * 1_000_000)
        soft_deadline_ns = start_time_ns + int((timeout_ms * 0.45) * 1_000_000)

        nodes_searched = [0]
        best_move_found = valid_moves[0]
        best_score_found = 0

        for current_depth in range(1, max_depth + 1):
            if current_depth > 1 and time.time_ns() > soft_deadline_ns:
                break
            if time.time_ns() > deadline_ns:
                break

            window_alpha = -999999
            window_beta = 999999
            window_delta = 50

            if current_depth > 1 and abs(best_score_found) < self.evaluator.WIN_SCORE // 2:
                window_alpha = best_score_found - window_delta
                window_beta = best_score_found + window_delta

            self._order_moves(valid_moves, best_move_found, 0, context)

            res_move, res_score, aborted = self._search_root(
                working_board, valid_moves, side, current_depth, window_alpha, window_beta, deadline_ns, context, nodes_searched
            )

            if aborted:
                break

            if current_depth > 1 and (res_score <= window_alpha or res_score >= window_beta):
                res_move, res_score, aborted = self._search_root(
                    working_board, valid_moves, side, current_depth, -999999, 999999, deadline_ns, context, nodes_searched
                )

            if not aborted and res_move is not None:
                best_move_found = res_move
                best_score_found = res_score

        duration_ms = (time.time_ns() - start_time_ns) // 1_000_000
        nps = (nodes_searched[0] * 1000 // duration_ms) if duration_ms > 0 else nodes_searched[0]
        logger.info(f"[AlphaBetaBotEngine] Depth: {max_depth} | Move: {best_move_found.from_pos}->{best_move_found.to_pos} | Score: {best_score_found} | Nodes: {nodes_searched[0]} (NPS: {nps})")

        return best_move_found

    def _search_root(self, board: Board, valid_moves: List[Move], side: Side, depth: int,
                     alpha: int, beta: int, deadline_ns: int, context: BotContext, nodes_searched: List[int]) -> Tuple[Optional[Move], int, bool]:
        iter_best_move = None
        iter_best_score = -999999
        aborted = False

        for move in valid_moves:
            if time.time_ns() > deadline_ns:
                aborted = True
                break

            board.make_move(move)
            next_key = Board.compute_zobrist_key(board.zobrist_hash, side.opposite)
            rep_count = context.get_position_count(next_key)

            nodes_searched[0] += 1
            score = self._minimax(board, depth - 1, 1, alpha, beta, False, side, deadline_ns, context, nodes_searched)
            if rep_count >= 1:
                score -= (rep_count * 3000)

            board.undo_move(move)

            if score > iter_best_score:
                iter_best_score = score
                iter_best_move = move

        return iter_best_move, iter_best_score, aborted

    def _minimax(self, board: Board, depth: int, ply: int, alpha: int, beta: int,
                 is_maximizing: bool, bot_side: Side, deadline_ns: int, context: BotContext, nodes_searched: List[int]) -> int:

        if time.time_ns() > deadline_ns:
            return self.evaluator.evaluate(board, bot_side)

        current_turn = bot_side if is_maximizing else bot_side.opposite
        pos_key = Board.compute_zobrist_key(board.zobrist_hash, current_turn)

        if context.get_position_count(pos_key) >= 2:
            return 0

        winner = self._fast_check_winner(board)
        if winner is not None:
            return self.evaluator.WIN_SCORE if winner == bot_side else self.evaluator.LOSS_SCORE

        if depth <= 0:
            return self._quiescence(board, alpha, beta, is_maximizing, bot_side, deadline_ns, context, nodes_searched, self.MAX_QS_DEPTH)

        alpha_orig, beta_orig = alpha, beta

        tt_entry = context.get_tt_entry(pos_key)
        if tt_entry is not None and tt_entry.depth >= depth:
            if tt_entry.flag == TtFlag.EXACT:
                return tt_entry.score
            elif tt_entry.flag == TtFlag.LOWER_BOUND:
                alpha = max(alpha, tt_entry.score)
            elif tt_entry.flag == TtFlag.UPPER_BOUND:
                beta = min(beta, tt_entry.score)
            if beta <= alpha:
                return tt_entry.score

        valid_moves = self.rule_engine.get_valid_moves(board, current_turn)
        if not valid_moves:
            return self.evaluator.LOSS_SCORE if is_maximizing else self.evaluator.WIN_SCORE

        tt_best_move = tt_entry.best_move if tt_entry is not None else None
        self._order_moves(valid_moves, tt_best_move, ply, context)

        context.push_position(pos_key)
        best_move_in_node = None

        try:
            if is_maximizing:
                max_eval = -999999
                for move in valid_moves:
                    board.make_move(move)
                    nodes_searched[0] += 1
                    eval_val = self._minimax(board, depth - 1, ply + 1, alpha, beta, False, bot_side, deadline_ns, context, nodes_searched)
                    board.undo_move(move)

                    if eval_val > max_eval:
                        max_eval = eval_val
                        best_move_in_node = move
                    alpha = max(alpha, eval_val)

                    if beta <= alpha:
                        context.store_killer_move(ply, move)
                        context.add_history_score(move, depth)
                        break
                best_eval = max_eval
            else:
                min_eval = 999999
                for move in valid_moves:
                    board.make_move(move)
                    nodes_searched[0] += 1
                    eval_val = self._minimax(board, depth - 1, ply + 1, alpha, beta, True, bot_side, deadline_ns, context, nodes_searched)
                    board.undo_move(move)

                    if eval_val < min_eval:
                        min_eval = eval_val
                        best_move_in_node = move
                    beta = min(beta, eval_val)

                    if beta <= alpha:
                        context.store_killer_move(ply, move)
                        context.add_history_score(move, depth)
                        break
                best_eval = min_eval
        finally:
            context.pop_position(pos_key)

        if best_eval <= alpha_orig:
            flag = TtFlag.UPPER_BOUND
        elif best_eval >= beta_orig:
            flag = TtFlag.LOWER_BOUND
        else:
            flag = TtFlag.EXACT

        context.store_tt_entry(pos_key, TtEntry(pos_key, depth, best_eval, flag, best_move_in_node))
        return best_eval

    def _quiescence(self, board: Board, alpha: int, beta: int, is_maximizing: bool,
                    bot_side: Side, deadline_ns: int, context: BotContext, nodes_searched: List[int], qs_depth: int) -> int:
        if time.time_ns() > deadline_ns:
            return self.evaluator.evaluate(board, bot_side)

        winner = self._fast_check_winner(board)
        if winner is not None:
            return self.evaluator.WIN_SCORE if winner == bot_side else self.evaluator.LOSS_SCORE

        stand_pat = self.evaluator.evaluate(board, bot_side)

        if is_maximizing:
            if stand_pat >= beta:
                return stand_pat
            alpha = max(alpha, stand_pat)
        else:
            if stand_pat <= alpha:
                return stand_pat
            beta = min(beta, stand_pat)

        if qs_depth <= 0:
            return stand_pat

        current_turn = bot_side if is_maximizing else bot_side.opposite
        valid_moves = self.rule_engine.get_valid_moves(board, current_turn)
        noisy_moves = [
            m for m in valid_moves 
            if m.captured_piece is not None or Board.is_den(m.to_pos.row, m.to_pos.col, current_turn.opposite)
        ]

        if not noisy_moves:
            return stand_pat

        self._order_moves(noisy_moves, None, 0, context)

        if is_maximizing:
            for move in noisy_moves:
                board.make_move(move)
                nodes_searched[0] += 1
                eval_val = self._quiescence(board, alpha, beta, False, bot_side, deadline_ns, context, nodes_searched, qs_depth - 1)
                board.undo_move(move)

                stand_pat = max(stand_pat, eval_val)
                alpha = max(alpha, eval_val)
                if beta <= alpha:
                    break
            return stand_pat
        else:
            for move in noisy_moves:
                board.make_move(move)
                nodes_searched[0] += 1
                eval_val = self._quiescence(board, alpha, beta, True, bot_side, deadline_ns, context, nodes_searched, qs_depth - 1)
                board.undo_move(move)

                stand_pat = min(stand_pat, eval_val)
                beta = min(beta, eval_val)
                if beta <= alpha:
                    break
            return stand_pat

    def _order_moves(self, moves: List[Move], primary_move: Optional[Move], ply: int, context: BotContext):
        moves.sort(key=lambda m: self._score_move(m, primary_move, ply, context), reverse=True)

    def _score_move(self, move: Move, primary_move: Optional[Move], ply: int, context: BotContext) -> int:
        if primary_move is not None and move == primary_move:
            return 20000

        enemy_side = move.moved_piece.side.opposite
        if Board.is_den(move.to_pos.row, move.to_pos.col, enemy_side):
            return 10000

        score = 0
        if move.captured_piece is not None:
            if move.moved_piece.type == PieceType.RAT and move.captured_piece.type == PieceType.ELEPHANT:
                score += 15000
            else:
                score += 1000 + (move.captured_piece.type.rank * 10 - move.moved_piece.type.rank)
        elif context is not None:
            killers = context.get_killer_moves(ply)
            if killers[0] == move:
                score += 900
            elif killers[1] == move:
                score += 800
            score += min(context.get_history_score(move), 500)

        enemy_den_row = 8 if move.moved_piece.side == Side.PLAYER_1 else 0
        dist_before = abs(move.from_pos.row - enemy_den_row) + abs(move.from_pos.col - 3)
        dist_after  = abs(move.to_pos.row   - enemy_den_row) + abs(move.to_pos.col   - 3)
        if dist_after < dist_before:
            score += 10

        return score

    def _fast_check_winner(self, board: Board) -> Optional[Side]:
        p1_in_p2_den = board.get_piece(self.P2_DEN_ROW, self.P2_DEN_COL)
        if p1_in_p2_den is not None and p1_in_p2_den.side == Side.PLAYER_1:
            return Side.PLAYER_1
        p2_in_p1_den = board.get_piece(self.P1_DEN_ROW, self.P1_DEN_COL)
        if p2_in_p1_den is not None and p2_in_p1_den.side == Side.PLAYER_2:
            return Side.PLAYER_2
        return None
