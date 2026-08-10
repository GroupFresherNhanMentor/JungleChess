package fpt.qn.junglechess.room.service;

import fpt.qn.junglechess.game.bot.BotDifficulty;
import fpt.qn.junglechess.game.bot.config.BotConfigService;
import fpt.qn.junglechess.game.rule.DefaultGameRuleEngine;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import fpt.qn.junglechess.room.dto.request.CreateRoomRequest;
import fpt.qn.junglechess.room.dto.response.CreateRoomResponse;
import fpt.qn.junglechess.room.model.GameMode;
import fpt.qn.junglechess.room.model.RoomState;
import fpt.qn.junglechess.room.repository.RoomStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoomBotDepthResolutionTest {

    private RoomService roomService;
    private BotConfigService botConfigService;
    private RoomStateRepository roomRepo;
    private GameRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        roomRepo = mock(RoomStateRepository.class);
        RoomEventBus eventBus = mock(RoomEventBus.class);
        LobbyService lobbyService = mock(LobbyService.class);
        ruleEngine = new DefaultGameRuleEngine();
        SessionRegistry sessionRegistry = mock(SessionRegistry.class);
        botConfigService = mock(BotConfigService.class);

        // save() returns Mono<Void> — use doReturn to avoid Mockito's when() calling existing stubs
        doReturn(Mono.empty()).when(roomRepo).save(any(RoomState.class));
        doReturn(Mono.empty()).when(roomRepo).saveSessionMapping(anyString(), anyString());
        doReturn(Flux.empty()).when(roomRepo).findAllActive();

        roomService = new RoomService(
                roomRepo, eventBus, lobbyService, ruleEngine, sessionRegistry, botConfigService);
    }

    @ParameterizedTest(name = "PVE room with {0} resolves correct search depth")
    @EnumSource(BotDifficulty.class)
    void pveRoomResolvesDepthFromDifficulty(BotDifficulty difficulty) {
        int expectedDepth = difficulty.getSearchDepth();
        when(botConfigService.resolveDepth(difficulty)).thenReturn(Mono.just(expectedDepth));

        AtomicReference<RoomState> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return Mono.empty();
        }).when(roomRepo).save(any(RoomState.class));

        CreateRoomRequest req = new CreateRoomRequest(GameMode.PVE, false, false, difficulty.name());
        CreateRoomResponse resp = roomService.createRoom(req, "session-1", "user-1").block();

        assertNotNull(resp);
        verify(botConfigService).resolveDepth(difficulty);
        assertNotNull(saved.get());
        assertEquals(expectedDepth, saved.get().getBotSearchDepth());
    }

    @Test
    @DisplayName("EVE room defaults to MEDIUM depth when difficulty is null")
    void eveRoomDefaultsToMediumWhenDifficultyNull() {
        when(botConfigService.resolveDepth(BotDifficulty.MEDIUM))
                .thenReturn(Mono.just(BotDifficulty.MEDIUM.getSearchDepth()));

        AtomicReference<RoomState> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return Mono.empty();
        }).when(roomRepo).save(any(RoomState.class));

        CreateRoomRequest req = new CreateRoomRequest(GameMode.EVE, true, true, null);
        roomService.createRoom(req, "admin-session", "admin").block();

        assertNotNull(saved.get());
        assertEquals(BotDifficulty.MEDIUM.getSearchDepth(), saved.get().getBotSearchDepth());
    }

    @Test
    @DisplayName("PVP room skips bot config resolution and stores depth 0")
    void pvpRoomStoresZeroDepth() {
        AtomicReference<RoomState> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return Mono.empty();
        }).when(roomRepo).save(any(RoomState.class));

        CreateRoomRequest req = new CreateRoomRequest(GameMode.PVP, false, false, null);
        roomService.createRoom(req, "session-1", "user-1").block();

        verifyNoInteractions(botConfigService);
        assertNotNull(saved.get());
        assertEquals(0, saved.get().getBotSearchDepth());
    }
}
