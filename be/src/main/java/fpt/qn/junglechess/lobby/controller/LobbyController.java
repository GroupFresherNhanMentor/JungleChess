package fpt.qn.junglechess.lobby.controller;

import fpt.qn.junglechess.room.dto.response.LobbySnapshot;
import fpt.qn.junglechess.room.service.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;
    private final SimpMessagingTemplate messaging;

    @MessageMapping("lobby.snapshot")
    public void getSnapshot(SimpMessageHeaderAccessor sha) {
        LobbySnapshot snapshot = lobbyService.getLatestSnapshot();
        var user = sha.getUser();
        if (user != null) {
            messaging.convertAndSendToUser(user.getName(), "/queue/lobby", snapshot);
        }
    }
}
