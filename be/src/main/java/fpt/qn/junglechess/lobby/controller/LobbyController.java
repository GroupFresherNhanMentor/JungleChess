package fpt.qn.junglechess.lobby.controller;

import fpt.qn.junglechess.room.dto.response.LobbySnapshot;
import fpt.qn.junglechess.room.service.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;

    @MessageMapping("lobby.rooms")
    public Flux<LobbySnapshot> lobbyRooms() {
        return lobbyService.stream();
    }
}
