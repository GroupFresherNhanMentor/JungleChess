package fpt.qn.junglechess.room.service;

import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    private final ConcurrentHashMap<String, RSocketRequester> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, RSocketRequester requester) {
        sessions.put(sessionId, requester);
    }

    public void deregister(String sessionId) {
        sessions.remove(sessionId);
    }

    public boolean isConnected(String sessionId) {
        RSocketRequester req = sessions.get(sessionId);
        return req != null && !req.isDisposed();
    }

    public RSocketRequester get(String sessionId) {
        return sessions.get(sessionId);
    }
}
