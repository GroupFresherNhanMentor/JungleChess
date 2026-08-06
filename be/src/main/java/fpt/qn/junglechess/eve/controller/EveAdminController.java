package fpt.qn.junglechess.eve.controller;

import fpt.qn.junglechess.eve.dto.EveRoomSnapshot;
import fpt.qn.junglechess.eve.service.DockerBotService;
import fpt.qn.junglechess.room.dto.request.CreateRoomRequest;
import fpt.qn.junglechess.room.dto.response.CreateRoomResponse;
import fpt.qn.junglechess.room.model.GameMode;
import fpt.qn.junglechess.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/room")
@RequiredArgsConstructor
public class EveAdminController {

    private final RoomService roomService;
    private final DockerBotService dockerBotService;

    @PostMapping("/eve")
    public Mono<CreateRoomResponse> createEveRoom() {
        CreateRoomRequest req = new CreateRoomRequest(GameMode.EVE, true, true, null);
        // Admin session/userId placeholder — real auth injected by co-worker's security layer
        return roomService.createRoom(req, "admin-session", "admin")
                .doOnSuccess(resp -> dockerBotService.spawnBotPair(resp.getRoomId()));
    }

    @MessageMapping("admin.eve.rooms")
    public Flux<EveRoomSnapshot> streamEveRooms() {
        return dockerBotService.stream();
    }
}
