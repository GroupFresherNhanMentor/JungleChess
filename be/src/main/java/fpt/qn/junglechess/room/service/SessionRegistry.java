package fpt.qn.junglechess.room.service;

import org.springframework.stereotype.Component;

import fpt.qn.junglechess.room.dto.response.BotOnlineInfo;
import fpt.qn.junglechess.security.JwtUserPrincipal;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    private final ConcurrentHashMap<String, StompSessionContext> sessions     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>              userToSession = new ConcurrentHashMap<>();

    public void register(String sessionId, JwtUserPrincipal principal) {
        sessions.put(sessionId, new StompSessionContext(sessionId, principal));
        if (principal != null) {
            userToSession.put(principal.userId().toString(), sessionId);
        }
    }

    public void deregister(String sessionId) {
        StompSessionContext ctx = sessions.remove(sessionId);
        if (ctx != null && ctx.principal() != null) {
            userToSession.remove(ctx.principal().userId().toString(), sessionId);
        }
    }

    public boolean isConnected(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    public String getUserId(String sessionId) {
        StompSessionContext ctx = sessions.get(sessionId);
        return (ctx != null && ctx.principal() != null)
                ? ctx.principal().userId().toString()
                : null;
    }

    public String getUsername(String sessionId) {
        StompSessionContext ctx = sessions.get(sessionId);
        return (ctx != null && ctx.principal() != null)
                ? ctx.principal().username()
                : null;
    }

    public String getSessionIdForUser(String userId) {
        return userToSession.get(userId);
    }

    public StompSessionContext findById(String sessionId) {
        return sessions.get(sessionId);
    }

    public String getUsernameByUserId(String userId) {
        String sessionId = userToSession.get(userId);
        if (sessionId == null) return null;
        StompSessionContext ctx = sessions.get(sessionId);
        return ctx != null && ctx.principal() != null ? ctx.principal().username() : null;
    }

    public List<BotOnlineInfo> getConnectedBots() {
        return sessions.values().stream()
                .filter(ctx -> ctx.principal() != null
                        && ctx.principal().roles().contains("BOT"))
                .map(ctx -> new BotOnlineInfo(
                        ctx.principal().userId().toString(),
                        ctx.principal().username()))
                .toList();
    }
}
