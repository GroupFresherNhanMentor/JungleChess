package fpt.qn.junglechess.botworker.controller;

import fpt.qn.junglechess.botworker.dto.BotAssignRequest;
import fpt.qn.junglechess.botworker.session.BotSessionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot")
public class BotAssignController {

    private final BotSessionManager manager;

    public BotAssignController(BotSessionManager manager) {
        this.manager = manager;
    }

    @PostMapping("/assign")
    public ResponseEntity<Void> assign(@RequestBody BotAssignRequest req) {
        manager.assign(req.roomId(), req.side(), req.difficulty());
        return ResponseEntity.accepted().build();
    }
}
