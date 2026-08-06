package fpt.qn.junglechess.game.bot.config;

import io.r2dbc.spi.ConnectionFactory;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import fpt.qn.junglechess.game.bot.BotDifficulty;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration-style unit test for {@link BotConfigService} against the local
 * dockerized PostgreSQL (localhost:5432/jc), seeded by the Flyway migration.
 * Verifies both the DB-backed and fallback paths of {@code resolveDepth}.
 */
class BotConfigServiceTest {

    private static DSLContext dsl;
    private BotConfigService service;

    @BeforeAll
    static void setUpDsl() {
        ConnectionFactory connectionFactory = new io.r2dbc.postgresql.PostgresqlConnectionFactory(
                io.r2dbc.postgresql.PostgresqlConnectionConfiguration.builder()
                        .host("localhost")
                        .port(5432)
                        .database("jc")
                        .username("postgres")
                        .password("postgres")
                        .build());

        DefaultConfiguration config = new DefaultConfiguration();
        config.set(connectionFactory);
        config.set(SQLDialect.POSTGRES);
        config.set(new Settings().withReturnAllOnUpdatableRecord(true));
        dsl = DSL.using(config);
    }

    @BeforeEach
    void setUp() {
        service = new BotConfigService(dsl);
    }

    @Test
    @DisplayName("Seeded bots table provides depth for each difficulty")
    void seededTableProvidesDepths() {
        assertEquals(Integer.valueOf(2), service.resolveDepth(BotDifficulty.EASY).block());
        assertEquals(Integer.valueOf(4), service.resolveDepth(BotDifficulty.MEDIUM).block());
        assertEquals(Integer.valueOf(6), service.resolveDepth(BotDifficulty.HARD).block());
    }

    @Test
    @DisplayName("Missing difficulty row falls back to enum default without error")
    void missingRowFallsBackToDefault() {
        // Delete the MEDIUM row to simulate an unseeded entry, then expect the enum default.
        Mono.from(dsl.deleteFrom(fpt.qn.junglechess.jooq.Tables.BOTS)
                .where(fpt.qn.junglechess.jooq.Tables.BOTS.DIFFICULTY.eq("MEDIUM")))
                .block();

        try {
            assertEquals(Integer.valueOf(4),
                    service.resolveDepth(BotDifficulty.MEDIUM).block(),
                    "resolveDepth must fall back to enum default when row is absent");
        } finally {
            // Restore the seeded row so subsequent tests see a consistent DB.
            Mono.from(dsl.insertInto(fpt.qn.junglechess.jooq.Tables.BOTS)
                    .set(fpt.qn.junglechess.jooq.Tables.BOTS.NAME, "Bot Trung Bình")
                    .set(fpt.qn.junglechess.jooq.Tables.BOTS.DIFFICULTY, "MEDIUM")
                    .set(fpt.qn.junglechess.jooq.Tables.BOTS.SEARCH_DEPTH, 4)
                    .set(fpt.qn.junglechess.jooq.Tables.BOTS.DESCRIPTION, "Cân bằng tốc độ và độ khó"))
                    .block();
        }
    }
}
