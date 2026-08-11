package fpt.qn.junglechess.room.controller;

import fpt.qn.junglechess.room.dto.response.BotOnlineInfo;
import fpt.qn.junglechess.room.service.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final SessionRegistry sessionRegistry;

    @GetMapping("/online")
    public List<BotOnlineInfo> getOnlineBots() {
        return sessionRegistry.getConnectedBots();
    }
}
