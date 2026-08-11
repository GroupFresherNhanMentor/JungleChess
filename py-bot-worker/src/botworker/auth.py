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

    def login_or_register(self) -> "BotTokens":
        """
        Tries to login first (account already exists from a previous run).
        If login fails, registers a new account and logs in.
        If registration returns 409 (someone else owns that username), raises with a clear message.
        """
        try:
            tokens = self.login()
            logger.info(f"Bot logged in as '{self.username}'")
            return tokens
        except Exception as login_ex:
            logger.info(f"Login failed ({login_ex}), attempting registration...")
            self._register()
            tokens = self.login()
            logger.info(f"Bot registered and logged in as '{self.username}'")
            return tokens

    def _register(self):
        url = f"{self.backend_url}/api/auth/bot-register"
        body = {"username": self.username, "password": self.password, "fullName": "Bot Worker Python"}
        resp = requests.post(url, json=body, timeout=10)
        if resp.status_code in (200, 201):
            logger.info(f"Bot account '{self.username}' registered successfully")
        elif resp.status_code == 409:
            raise RuntimeError(
                f"Username '{self.username}' is already taken by another account. "
                f"Please set a unique BOT_USERNAME for your bot and restart."
            )
        else:
            resp.raise_for_status()

    def login(self) -> "BotTokens":
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
