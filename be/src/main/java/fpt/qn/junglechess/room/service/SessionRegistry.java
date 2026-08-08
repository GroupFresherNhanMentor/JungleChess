package fpt.qn.junglechess.room.service;

import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

import fpt.qn.junglechess.security.JwtUserPrincipal;

@Component
public class SessionRegistry {

    private final ConcurrentHashMap<String, RSocketSessionContext> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> requesterSessions = new ConcurrentHashMap<>();

    public void register(String sessionId, RSocketRequester requester, JwtUserPrincipal principal) {
        sessions.put(sessionId, new RSocketSessionContext(sessionId, requester, principal));
        requesterSessions.put(System.identityHashCode(requester), sessionId);
    }

    public void deregister(String sessionId) {
        RSocketSessionContext context = sessions.remove(sessionId);
        if (context != null) {
            requesterSessions.remove(System.identityHashCode(context.requester()));
        }
    }

    public boolean isConnected(String sessionId) {
        RSocketSessionContext context = sessions.get(sessionId);
        return context != null && !context.requester().isDisposed();
    }

    public RSocketRequester get(String sessionId) {
        RSocketSessionContext context = sessions.get(sessionId);
        return context != null ? context.requester() : null;
    }

    public RSocketSessionContext findByRequester(RSocketRequester requester) {
        String sessionId = requesterSessions.get(System.identityHashCode(requester));
        return sessionId != null ? sessions.get(sessionId) : null;
    }
}
