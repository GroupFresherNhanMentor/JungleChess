package fpt.qn.junglechess.game.bot.config;

import static fpt.qn.junglechess.jooq.Tables.BOTS;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import fpt.qn.junglechess.game.bot.BotDifficulty;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Resolves a {@link BotDifficulty} to a concrete search depth for the bot engine.
 *
 * <p>Prefers the {@code bots} table row matching the difficulty (DB is the source
 * of truth for bot configuration) and falls back to the enum default when no row
 * exists, so room creation never fails on a missing/unseeded table.
 */
@Component
@RequiredArgsConstructor
public class BotConfigService {

    private final DSLContext dsl;

    /**
     * @param difficulty the requested difficulty (must be non-null)
     * @return the resolved search depth: the {@code bots} table value if a row
     *         exists, otherwise the enum's default depth
     */
    public Mono<Integer> resolveDepth(BotDifficulty difficulty) {
        return Mono.from(
                dsl.select(BOTS.SEARCH_DEPTH)
                        .from(BOTS)
                        .where(BOTS.DIFFICULTY.eq(difficulty.name()))
                        .limit(1)
        )
                .map(r -> r.value1())
                .defaultIfEmpty(difficulty.getSearchDepth());
    }
}
