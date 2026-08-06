package fpt.qn.junglechess.eve.service;

import fpt.qn.junglechess.eve.dto.BotContainerInfo;
import fpt.qn.junglechess.eve.dto.ContainerHealth;
import fpt.qn.junglechess.eve.dto.EveRoomEntry;
import fpt.qn.junglechess.eve.dto.EveRoomSnapshot;
import fpt.qn.junglechess.room.model.RoomState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DockerBotService {

    @Value("${app.eve.bot-image:junglechess-bot:latest}")
    private String botImage;

    @Value("${app.eve.server-url:ws://localhost:7070/rsocket}")
    private String serverUrl;

    // roomId → [containerId for P1, containerId for P2]
    private final Map<String, String[]> roomContainers = new ConcurrentHashMap<>();

    private final Sinks.Many<EveRoomSnapshot> eveSink =
            Sinks.many().replay().latest();

    public void spawnBotPair(String roomId) {
        String cId1 = spawnContainer(roomId, "PLAYER_1");
        String cId2 = spawnContainer(roomId, "PLAYER_2");
        if (cId1 != null && cId2 != null) {
            roomContainers.put(roomId, new String[]{cId1, cId2});
            emitSnapshot();
        }
    }

    private String spawnContainer(String roomId, String side) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "run", "--rm", "-d",
                    "-e", "ROOM_ID=" + roomId,
                    "-e", "SERVER_URL=" + serverUrl,
                    "-e", "SIDE=" + side,
                    botImage
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String containerId = new String(proc.getInputStream().readAllBytes()).trim();
            int exit = proc.waitFor();
            if (exit != 0 || containerId.isBlank()) {
                log.error("Failed to start bot container for room {} side {}", roomId, side);
                return null;
            }
            log.info("Started bot container {} for room {} side {}", containerId, roomId, side);
            return containerId;
        } catch (Exception e) {
            log.error("Error spawning bot container for room {} side {}: {}", roomId, side, e.getMessage());
            return null;
        }
    }

    public ContainerHealth inspectContainer(String containerId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "inspect", "--format", "{{.State.Status}}", containerId);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String status = new String(proc.getInputStream().readAllBytes()).trim();
            proc.waitFor();
            return switch (status) {
                case "running"    -> ContainerHealth.RUNNING;
                case "exited"     -> ContainerHealth.EXITED;
                default           -> ContainerHealth.UNKNOWN;
            };
        } catch (Exception e) {
            return ContainerHealth.ERROR;
        }
    }

    public void onRoomUpdated(RoomState state) {
        if (roomContainers.containsKey(state.getRoomId())) {
            emitSnapshot();
        }
    }

    public Flux<EveRoomSnapshot> stream() {
        // Merge the event-driven sink with a 10s health poll
        Flux<EveRoomSnapshot> polled = Flux.interval(Duration.ofSeconds(10))
                .map(t -> buildSnapshot())
                .filter(snap -> !snap.getRooms().isEmpty());
        return Flux.merge(eveSink.asFlux(), polled);
    }

    private void emitSnapshot() {
        eveSink.tryEmitNext(buildSnapshot());
    }

    private EveRoomSnapshot buildSnapshot() {
        List<EveRoomEntry> entries = new ArrayList<>();
        roomContainers.forEach((roomId, containerIds) -> {
            BotContainerInfo bot1 = BotContainerInfo.builder()
                    .containerId(containerIds[0])
                    .side("PLAYER_1")
                    .health(inspectContainer(containerIds[0]))
                    .build();
            BotContainerInfo bot2 = BotContainerInfo.builder()
                    .containerId(containerIds[1])
                    .side("PLAYER_2")
                    .health(inspectContainer(containerIds[1]))
                    .build();
            entries.add(EveRoomEntry.builder()
                    .roomId(roomId)
                    .bot1(bot1)
                    .bot2(bot2)
                    .build());
        });
        return EveRoomSnapshot.of(entries);
    }

    public void removeRoom(String roomId) {
        roomContainers.remove(roomId);
        emitSnapshot();
    }
}
