import os
import sys
import time
import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("PyBotWorker")

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "src"))

from botworker.auth import BotAuthClient
from botworker.stomp_client import StompConnectionService
from botworker.session import BotSessionManager
from game.bot import AlphaBetaBotEngine

def main():
    server_url = os.getenv("SERVER_URL", "ws://localhost:8080/ws")
    backend_url = os.getenv("BACKEND_HTTP_URL", "http://localhost:8080")
    bot_username = os.getenv("BOT_USERNAME", "bot-worker")
    bot_password = os.getenv("BOT_PASSWORD", "Bot@worker1")
    manual_room = os.getenv("ROOM_ID")
    manual_side = os.getenv("SIDE", "PLAYER_2")
    manual_diff = os.getenv("DIFFICULTY", "MEDIUM")

    logger.info("=== Starting Python Jungle Chess Bot Worker Microservice ===")
    logger.info(f"Server URL: {server_url}")
    logger.info(f"Backend HTTP URL: {backend_url}")
    logger.info(f"Bot Username: {bot_username}")

    auth_client = BotAuthClient(backend_url, bot_username, bot_password)
    bot_engine = AlphaBetaBotEngine()
    stomp_service = StompConnectionService(server_url, auth_client)
    session_manager = BotSessionManager(stomp_service, bot_engine)

    stomp_service.start()

    if manual_room:
        logger.info(f"Manual room assignment detected: room={manual_room}, side={manual_side}, difficulty={manual_diff}")
        session_manager.assign(manual_room, manual_side, manual_diff)

    try:
        while True:
            time.sleep(5)
    except KeyboardInterrupt:
        logger.info("Shutting down Python bot worker service...")

if __name__ == "__main__":
    main()
