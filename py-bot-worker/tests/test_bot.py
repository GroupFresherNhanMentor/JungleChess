import os
import sys
import pytest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "../src")))

from game.model import Board, Piece, PieceType, Position, Side
from game.rule import DefaultGameRuleEngine
from game.evaluator import BoardEvaluator
from game.bot import AlphaBetaBotEngine, BotContext

def test_initial_board_valid_moves():
    board = Board.create_initial()
    rule_engine = DefaultGameRuleEngine()
    moves_p1 = rule_engine.get_valid_moves(board, Side.PLAYER_1)
    moves_p2 = rule_engine.get_valid_moves(board, Side.PLAYER_2)
    assert len(moves_p1) > 0
    assert len(moves_p2) > 0

def test_board_evaluator():
    board = Board.create_initial()
    evaluator = BoardEvaluator()
    score_p1 = evaluator.evaluate(board, Side.PLAYER_1)
    score_p2 = evaluator.evaluate(board, Side.PLAYER_2)
    assert score_p1 == -score_p2 or abs(score_p1 - score_p2) < 500

def test_alpha_beta_bot_engine():
    board = Board.create_initial()
    engine = AlphaBetaBotEngine()
    context = BotContext()
    move = engine.next_move(board, Side.PLAYER_1, max_depth=2, timeout_ms=1000, context=context)
    assert move is not None
    assert move.from_pos is not None
    assert move.to_pos is not None
