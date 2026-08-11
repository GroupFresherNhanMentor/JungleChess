import logging
import requests
from typing import NamedTuple

logger = logging.getLogger("BotAuthClient")

class BotTokens(NamedTuple):
    access_token: str
    refresh_token: str

class BotAuthClient:
    def __init__(self, backend_url: str, username: str, password: str):
        self.backend_url = backend_url.rstrip("/")
        self.username = username
        self.password = password

    def register(self):
        url = f"{self.backend_url}/api/auth/bot-register"
        body = {
            "username": self.username,
            "password": self.password,
            "fullName": "Bot Worker Python"
        }
        try:
            resp = requests.post(url, json=body, timeout=10)
            if resp.status_code == 200 or resp.status_code == 201:
                logger.info("Bot account registered successfully")
            elif resp.status_code == 409:
                logger.debug("Bot account already exists, skipping registration")
            else:
                logger.warning(f"Bot registration status {resp.status_code}: {resp.text}")
        except Exception as e:
            logger.warning(f"Bot registration call failed: {e}")

    def login(self) -> BotTokens:
        url = f"{self.backend_url}/api/auth/login"
        body = {
            "username": self.username,
            "password": self.password
        }
        return self._extract_tokens(url, body)

    def refresh(self, refresh_token: str) -> BotTokens:
        url = f"{self.backend_url}/api/auth/refresh"
        body = {"refreshToken": refresh_token}
        return self._extract_tokens(url, body)

    def _extract_tokens(self, url: str, body: dict) -> BotTokens:
        resp = requests.post(url, json=body, timeout=10)
        resp.raise_for_status()
        res_json = resp.json()
        data = res_json.get("data", {})
        access_token = data.get("accessToken")
        refresh_token_val = data.get("refreshToken")
        if not access_token or not refresh_token_val:
            raise ValueError(f"Missing tokens in response from {url}: {res_json}")
        return BotTokens(access_token, refresh_token_val)
