package fpt.qn.junglechess.user.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import fpt.qn.junglechess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuestCleanupScheduler {

    private final UserRepository userRepository;

    @Value("${app.auth.guest-retention:7d}")
    private Duration guestRetention;

    @Scheduled(cron = "${app.auth.guest-cleanup-cron:0 0 3 * * *}", zone = "UTC")
    public void deleteExpiredGuests() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(guestRetention);

        userRepository.deleteExpiredGuests(cutoff)
                .doOnSuccess(ignored -> log.info("Expired guest cleanup completed before {}", cutoff))
                .doOnError(error -> log.error("Expired guest cleanup failed", error))
                .subscribe();
    }
}
