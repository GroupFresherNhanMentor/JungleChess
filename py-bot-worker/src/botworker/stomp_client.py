import json
import time
import logging
import threading
from typing import Callable, Dict, List, Optional
import websocket

logger = logging.getLogger("StompConnectionService")

class StompFrame:
    def __init__(self, command: str, headers: Dict[str, str], body: str = ""):
        self.command = command
        self.headers = headers
        self.body = body

    @classmethod
    def parse(cls, raw: str) -> Optional["StompFrame"]:
        if not raw or raw == "\n":
            return None
        lines = raw.rstrip("\x00").split("\n")
        if not lines:
            return None
        command = lines[0].strip()
        headers = {}
        idx = 1
        while idx < len(lines) and lines[idx]:
            header_line = lines[idx]
            if ":" in header_line:
                k, v = header_line.split(":", 1)
                headers[k.strip()] = v.strip()
            idx += 1
        body = "\n".join(lines[idx + 1:]) if idx + 1 < len(lines) else ""
        return cls(command, headers, body)

    def serialize(self) -> str:
        res = [self.command]
        for k, v in self.headers.items():
            res.append(f"{k}:{v}")
        res.append("")
        res.append(self.body)
        return "\n".join(res) + "\x00"

class StompConnectionService:
    def __init__(self, server_url: str, auth_client):
        self.server_url = server_url
        self.auth_client = auth_client
        self.ws: Optional[websocket.WebSocketApp] = None
        self.connected = False
        self.sub_id_counter = 0
        self.subscriptions: Dict[str, Tuple[str, Callable[[dict], None]]] = {} # topic -> (sub_id, callback)
        self.on_connected_callbacks: List[Callable[[], None]] = []
        self.on_assign_callback: Optional[Callable[[dict], None]] = None
        self.access_token: Optional[str] = None
        self.refresh_token: Optional[str] = None
        self.worker_thread: Optional[threading.Thread] = None
        self.stop_requested = False

    def add_on_connected_callback(self, cb: Callable[[], None]):
        self.on_connected_callbacks.append(cb)

    def set_on_assign_callback(self, cb: Callable[[dict], None]):
        self.on_assign_callback = cb

    def start(self):
        self.worker_thread = threading.Thread(target=self._run_loop, daemon=True)
        self.worker_thread.start()

    def _run_loop(self):
        attempt = 0
        while not self.stop_requested:
            try:
                self.auth_client.register()
                tokens = self.auth_client.login()
                self.access_token = tokens.access_token
                self.refresh_token = tokens.refresh_token
                attempt = 0

                headers = {}
                self.ws = websocket.WebSocketApp(
                    self.server_url,
                    header=headers,
                    on_open=self._on_ws_open,
                    on_message=self._on_ws_message,
                    on_error=self._on_ws_error,
                    on_close=self._on_ws_close
                )
                self.ws.run_forever(ping_interval=20, ping_timeout=10)
            except Exception as e:
                logger.error(f"STOMP connection loop error: {e}")

            attempt += 1
            delay = min(2 ** attempt, 60)
            logger.info(f"Reconnecting STOMP in {delay}s...")
            time.sleep(delay)

    def _on_ws_open(self, ws):
        logger.info(f"WebSocket opened to {self.server_url}, sending CONNECT frame...")
        stomp_headers = {
            "accept-version": "1.1,1.2",
            "heart-beat": "10000,10000"
        }
        if self.access_token:
            stomp_headers["Authorization"] = f"Bearer {self.access_token}"
        
        connect_frame = StompFrame("CONNECT", stomp_headers)
        ws.send(connect_frame.serialize())

    def _on_ws_message(self, ws, message):
        frame = StompFrame.parse(message)
        if frame is None:
            return

        if frame.command == "CONNECTED":
            self.connected = True
            logger.info("STOMP session CONNECTED!")
            
            # Subscribe to bot invite queue
            self._send_subscribe("/user/queue/bot-invite", "sub-bot-invite")

            # Resubscribe topics
            for topic, (sub_id, _) in list(self.subscriptions.items()):
                self._send_subscribe(topic, sub_id)

            for cb in self.on_connected_callbacks:
                try:
                    cb()
                except Exception as e:
                    logger.error(f"Error in on_connected callback: {e}")

        elif frame.command == "MESSAGE":
            dest = frame.headers.get("destination", "")
            body = frame.body
            payload = {}
            if body:
                try:
                    payload = json.loads(body)
                except Exception as e:
                    logger.warning(f"Failed to parse JSON body: {body}")

            if dest == "/user/queue/bot-invite" or dest.endswith("/queue/bot-invite"):
                if self.on_assign_callback:
                    self.on_assign_callback(payload)
            else:
                for topic, (_, cb) in list(self.subscriptions.items()):
                    if dest == topic or dest.endswith(topic):
                        cb(payload)

        elif frame.command == "ERROR":
            logger.error(f"STOMP ERROR frame received: {frame.headers.get('message')} | {frame.body}")

    def _on_ws_error(self, ws, error):
        logger.error(f"WebSocket transport error: {error}")
        self.connected = False

    def _on_ws_close(self, ws, close_status_code, close_msg):
        logger.warning(f"WebSocket closed: {close_status_code} - {close_msg}")
        self.connected = False

    def subscribe(self, topic: str, callback: Callable[[dict], None]):
        if topic not in self.subscriptions:
            self.sub_id_counter += 1
            sub_id = f"sub-{self.sub_id_counter}"
            self.subscriptions[topic] = (sub_id, callback)
            if self.connected:
                self._send_subscribe(topic, sub_id)
        else:
            sub_id, _ = self.subscriptions[topic]
            self.subscriptions[topic] = (sub_id, callback)

    def _send_subscribe(self, topic: str, sub_id: str):
        if self.ws and self.connected:
            frame = StompFrame("SUBSCRIBE", {
                "id": sub_id,
                "destination": topic
            })
            self.ws.send(frame.serialize())

    def send(self, destination: str, payload: dict):
        if self.ws and self.connected:
            body = json.dumps(payload)
            frame = StompFrame("SEND", {
                "destination": destination,
                "content-type": "application/json"
            }, body)
            self.ws.send(frame.serialize())
        else:
            logger.warning(f"Cannot send to {destination} - STOMP not connected")

    def is_connected(self) -> bool:
        return self.connected
