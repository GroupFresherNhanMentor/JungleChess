package fpt.qn.junglechess.room.service;

import org.springframework.stereotype.Component;

import fpt.qn.junglechess.security.JwtUserPrincipal;

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
}
