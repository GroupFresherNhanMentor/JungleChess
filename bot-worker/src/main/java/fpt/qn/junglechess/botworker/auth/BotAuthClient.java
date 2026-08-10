package fpt.qn.junglechess.botworker.auth;

import fpt.qn.junglechess.botworker.config.BotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class BotAuthClient {

    private static final Logger log = LoggerFactory.getLogger(BotAuthClient.class);

    private final RestTemplate rest = new RestTemplate();
    private final BotProperties props;

    public record BotTokens(String accessToken, String refreshToken) {}

    public BotAuthClient(BotProperties props) {
        this.props = props;
    }

    /**
     * Registers the bot account with BOT role. Idempotent: if the account already
     * exists the server returns 409 and we silently ignore it.
     */
    public void register() {
        String url = props.getBackendHttpUrl() + "/api/auth/bot-register";
        Map<String, String> body = Map.of(
                "username", props.getBotUsername(),
                "password", props.getBotPassword(),
                "fullName", "Bot Worker"
        );
        try {
            rest.postForEntity(url, body, Map.class);
            log.info("Bot account registered successfully");
        } catch (HttpClientErrorException.Conflict e) {
            log.debug("Bot account already exists, skipping registration");
        } catch (Exception e) {
            log.warn("Bot registration call failed: {}", e.getMessage());
        }
    }

    public BotTokens login() {
        String url = props.getBackendHttpUrl() + "/api/auth/login";
        Map<String, String> body = Map.of(
                "username", props.getBotUsername(),
                "password", props.getBotPassword()
        );
        return extractTokens(url, body);
    }

    public BotTokens refresh(String refreshToken) {
        String url = props.getBackendHttpUrl() + "/api/auth/refresh";
        Map<String, String> body = Map.of("refreshToken", refreshToken);
        return extractTokens(url, body);
    }

    @SuppressWarnings("unchecked")
    private BotTokens extractTokens(String url, Object body) {
        ResponseEntity<Map> response = rest.postForEntity(url, body, Map.class);
        if (response.getBody() == null) throw new IllegalStateException("Empty response from " + url);

        Object dataObj = response.getBody().get("data");
        if (!(dataObj instanceof Map<?, ?> data)) {
            throw new IllegalStateException("Unexpected response shape from " + url);
        }

        String accessToken = (String) data.get("accessToken");
        String refreshTokenValue = (String) data.get("refreshToken");
        if (accessToken == null || refreshTokenValue == null) {
            throw new IllegalStateException("Missing tokens in response from " + url);
        }

        log.debug("Received tokens from {}", url);
        return new BotTokens(accessToken, refreshTokenValue);
    }
}
