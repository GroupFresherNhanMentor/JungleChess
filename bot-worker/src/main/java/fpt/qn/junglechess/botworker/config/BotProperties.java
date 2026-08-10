package fpt.qn.junglechess.botworker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    private String serverUrl = "ws://localhost:8080/ws";
    private String backendHttpUrl = "http://localhost:8080";
    private String botUsername = "";
    private String botPassword = "";

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getBackendHttpUrl() { return backendHttpUrl; }
    public void setBackendHttpUrl(String backendHttpUrl) { this.backendHttpUrl = backendHttpUrl; }

    public String getBotUsername() { return botUsername; }
    public void setBotUsername(String botUsername) { this.botUsername = botUsername; }

    public String getBotPassword() { return botPassword; }
    public void setBotPassword(String botPassword) { this.botPassword = botPassword; }
}
