package fpt.qn.junglechess.room.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DisconnectScheduler {

    static final long GRACE_SECONDS = 30;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public void schedule(String userId, Runnable onTimeout) {
        cancel(userId);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pending.remove(userId);
            log.debug("Disconnect grace expired for user {}, cleaning up", userId);
            onTimeout.run();
        }, GRACE_SECONDS, TimeUnit.SECONDS);
        pending.put(userId, future);
        log.debug("Disconnect grace started for user {} ({}s)", userId, GRACE_SECONDS);
    }

    public boolean cancel(String userId) {
        ScheduledFuture<?> f = pending.remove(userId);
        if (f != null && !f.isDone()) {
            f.cancel(false);
            log.debug("Disconnect cancelled for user {} — reconnected in time", userId);
            return true;
        }
        return false;
    }
}
