import time
import logging
import threading
from typing import Dict, List, Optional
from concurrent.futures import ThreadPoolExecutor
from game.model import Board, Piece, PieceType, Position, Side
from game.bot import AlphaBetaBotEngine, BotContext

logger = logging.getLogger("BotGameSession")

MIN_MOVE_MS = 1200

class BotGameSession:
    def __init__(self, room_id: str, side: str, difficulty: str, bot_engine: AlphaBetaBotEngine, connection):
        self.room_id = room_id
        self.side = side.upper()
        self.difficulty = difficulty.upper()
        self.bot_engine = bot_engine
        self.connection = connection
        self.executor = ThreadPoolExecutor(max_workers=1)
        self.bot_context = BotContext()
        self.ended = False

        if self.difficulty == "EASY":
            self.search_depth = 2
        elif self.difficulty == "HARD":
            self.search_depth = 6
        else:
            self.search_depth = 4

    def handle_event(self, event_map: dict):
        if self.ended:
            return

        event_type = event_map.get("type")
        if event_type == "STATE_UPDATED" or "board" in event_map:
            self._handle_state_update(event_map)
        elif event_type == "GAME_RESULT" or "winner" in event_map:
            self.ended = True
            logger.info(f"[Room {self.room_id}] Game ended — shutting down session")
            self.executor.shutdown(wait=False)

    def _handle_state_update(self, event_map: dict):
        status = event_map.get("status")
        current_turn = event_map.get("currentTurn")

        if status == "ENDED":
            self.ended = True
            self.executor.shutdown(wait=False)
            return

        if status != "PLAYING":
            return

        board_arr = self._parse_board(event_map)
        board = self._board_from_array(board_arr)
        turn_side = Side[current_turn.upper()] if current_turn else Side.PLAYER_1
        pos_key = Board.compute_zobrist_key(board.zobrist_hash, turn_side)
        self.bot_context.record_position(pos_key)

        if self.side.lower() == str(current_turn).lower():
            logger.debug(f"[Room {self.room_id}] Bot's turn ({self.side}), computing move...")
            self.executor.submit(self._compute_and_send_move, board)

    def _parse_board(self, event_map: dict) -> List[List[Optional[str]]]:
        result = [[None for _ in range(Board.COLS)] for _ in range(Board.ROWS)]
        raw_board = event_map.get("board")
        if not isinstance(raw_board, list):
            return result

        for r, row_data in enumerate(raw_board):
            if r < Board.ROWS and isinstance(row_data, list):
                for c, cell in enumerate(row_data):
                    if c < Board.COLS and cell is not None:
                        code = str(cell).strip()
                        if code and code.lower() != "null":
                            result[r][c] = code
        return result

    def _board_from_array(self, arr: List[List[Optional[str]]]) -> Board:
        board = Board()
        for r in range(len(arr)):
            if r >= Board.ROWS:
                break
            for c in range(len(arr[r])):
                if c >= Board.COLS:
                    break
                code = arr[r][c]
                if code:
                    idx = code.rfind('_')
                    if idx > 0:
                        side_str = code[:idx]
                        type_str = code[idx+1:]
                        try:
                            s = Side[side_str]
                            t = PieceType[type_str]
                            board.set_piece(r, c, Piece(s, t))
                        except Exception as e:
                            logger.warning(f"Error parsing piece code '{code}': {e}")
        board.recompute_zobrist()
        return board

    def _compute_and_send_move(self, board: Board):
        start_ms = int(time.time() * 1000)
        try:
            bot_side = Side[self.side]
            best_move = self.bot_engine.next_move(board, bot_side, self.search_depth, 2500, self.bot_context)
            if best_move is not None:
                elapsed = int(time.time() * 1000) - start_ms
                remaining = MIN_MOVE_MS - elapsed
                if remaining > 0:
                    time.sleep(remaining / 1000.0)

                req = {
                    "from": [best_move.from_pos.row, best_move.from_pos.col],
                    "to": [best_move.to_pos.row, best_move.to_pos.col]
                }
                self.connection.send(f"/app/room/{self.room_id}/move", req)
                logger.info(f"[Room {self.room_id}] Bot move sent: ({best_move.from_pos.row},{best_move.from_pos.col}) -> ({best_move.to_pos.row},{best_move.to_pos.col})")
            else:
                logger.warning(f"[Room {self.room_id}] No legal move found!")
        except Exception as e:
            logger.error(f"[Room {self.room_id}] Error computing move: {e}")

class BotSessionManager:
    def __init__(self, connection, bot_engine: AlphaBetaBotEngine):
        self.connection = connection
        self.bot_engine = bot_engine
        self.sessions: Dict[str, BotGameSession] = {} # key: roomId:side
        self.topic_sessions: Dict[str, List[BotGameSession]] = {} # key: /topic/room/{roomId}
        self.pending_queue: List[dict] = []
        self.lock = threading.Lock()

        connection.add_on_connected_callback(self._process_pending)
        connection.set_on_assign_callback(self._handle_assign_message)

    def _handle_assign_message(self, map_data: dict):
        room_id = map_data.get("roomId")
        side = map_data.get("side")
        difficulty = map_data.get("difficulty", "MEDIUM")
        if room_id and side:
            self.assign(room_id, side, difficulty)
        else:
            logger.warning(f"Received malformed assign message: {map_data}")

    def assign(self, room_id: str, side: str, difficulty: str):
        key = f"{room_id}:{side}"
        with self.lock:
            if key in self.sessions:
                logger.warning(f"Bot already assigned to room {room_id} as {side}")
                return
            if not self.connection.is_connected():
                logger.info(f"STOMP not ready, queuing assignment for room {room_id} side {side}")
                self.pending_queue.append({"roomId": room_id, "side": side, "difficulty": difficulty})
                return
            self._do_assign(room_id, side, difficulty)

    def _process_pending(self):
        with self.lock:
            for s in list(self.sessions.values()):
                if not s.ended:
                    self.connection.send(
                        f"/app/room/{s.room_id}/bot-join",
                        {"side": s.side, "difficulty": s.difficulty}
                    )

            to_process = list(self.pending_queue)
            self.pending_queue.clear()
            for p in to_process:
                self._do_assign(p["roomId"], p["side"], p["difficulty"])

    def _do_assign(self, room_id: str, side: str, difficulty: str):
        session = BotGameSession(room_id, side, difficulty, self.bot_engine, self.connection)
        self.sessions[f"{room_id}:{side}"] = session

        topic = f"/topic/room/{room_id}"
        if topic not in self.topic_sessions:
            self.topic_sessions[topic] = []
        self.topic_sessions[topic].append(session)

        if len(self.topic_sessions[topic]) == 1:
            def handler(payload: dict):
                active = self.topic_sessions.get(topic)
                if not active:
                    return
                to_remove = []
                for s in active:
                    s.handle_event(payload)
                    if s.ended:
                        self.sessions.pop(f"{s.room_id}:{s.side}", None)
                        to_remove.append(s)
                        logger.info(f"Removed ended session room={s.room_id} side={s.side}")
                for s in to_remove:
                    active.remove(s)
                if not active:
                    self.topic_sessions.pop(topic, None)

            self.connection.subscribe(topic, handler)

        self.connection.send(
            f"/app/room/{room_id}/bot-join",
            {"side": side, "difficulty": difficulty}
        )
        logger.info(f"Bot assigned: room={room_id} side={side} difficulty={difficulty}")

    def active_session_count(self) -> int:
        with self.lock:
            return sum(1 for s in self.sessions.values() if not s.ended)
