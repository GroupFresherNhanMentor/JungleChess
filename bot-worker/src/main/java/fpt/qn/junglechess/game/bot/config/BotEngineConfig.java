package fpt.qn.junglechess.game.bot.config;

import java.util.Random;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link Random} injected into {@code AlphaBetaBotEngine} so tied
 * best moves are broken non-deterministically in EvE matches (and repeated
 * queries), while keeping the source injectable and testable.
 */
@Configuration
public class BotEngineConfig {

    @Bean
    public Random botRandom() {
        return new Random();
    }
}