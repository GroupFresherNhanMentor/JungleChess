package fpt.qn.junglechess.room.service;

import fpt.qn.junglechess.room.dto.event.RoomEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomEventBus {

    private final ConcurrentHashMap<String, Sinks.Many<RoomEvent>> roomSinks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Sinks.Many<RoomEvent>> sessionSinks = new ConcurrentHashMap<>();

    public Sinks.Many<RoomEvent> getRoomSink(String roomId) {
        return roomSinks.computeIfAbsent(roomId,
                id -> Sinks.many().multicast().onBackpressureBuffer());
    }

    public Sinks.Many<RoomEvent> getSessionSink(String sessionId) {
        return sessionSinks.computeIfAbsent(sessionId,
                id -> Sinks.many().multicast().onBackpressureBuffer());
    }

    public void emit(String roomId, RoomEvent event) {
        Sinks.Many<RoomEvent> sink = roomSinks.get(roomId);
        if (sink != null) {
            sink.tryEmitNext(event);
        }
    }

    public void emitToSession(String sessionId, RoomEvent event) {
        Sinks.Many<RoomEvent> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.tryEmitNext(event);
        }
    }

    public Flux<RoomEvent> subscribeRoom(String roomId, String sessionId) {
        return Flux.merge(
                getRoomSink(roomId).asFlux(),
                getSessionSink(sessionId).asFlux()
        );
    }

    public void destroyRoom(String roomId) {
        Sinks.Many<RoomEvent> sink = roomSinks.remove(roomId);
        if (sink != null) sink.tryEmitComplete();
    }

    public void destroySession(String sessionId) {
        Sinks.Many<RoomEvent> sink = sessionSinks.remove(sessionId);
        if (sink != null) sink.tryEmitComplete();
    }
}
