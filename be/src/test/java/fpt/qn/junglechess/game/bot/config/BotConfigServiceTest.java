package fpt.qn.junglechess.game.bot.config;

import fpt.qn.junglechess.game.bot.BotDifficulty;
import fpt.qn.junglechess.jooq.Tables;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DB-independent unit test for {@link BotConfigService}.
 * Uses jOOQ's built-in {@link MockDataProvider} to simulate database queries
 * in memory without requiring a running PostgreSQL server.
 */
class BotConfigServiceTest {

    @Test
    @DisplayName("DB provides search depth when difficulty row exists")
    void dbProvidesDepthWhenRowExists() {
        MockDataProvider provider = context -> {
            DSLContext create = DSL.using(SQLDialect.POSTGRES);
            Result<Record1<Integer>> result = create.newResult(Tables.BOTS.SEARCH_DEPTH);
            Record1<Integer> record = create.newRecord(Tables.BOTS.SEARCH_DEPTH);
            record.value1(6);
            result.add(record);
            return new MockResult[] { new MockResult(1, result) };
        };

        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
        BotConfigService service = new BotConfigService(dsl);

        Integer depth = service.resolveDepth(BotDifficulty.HARD).block();
        assertEquals(6, depth);
    }

    @Test
    @DisplayName("Falls back to enum default depth when DB returns empty")
    void fallsBackToEnumDefaultWhenEmpty() {
        MockDataProvider provider = context -> {
            DSLContext create = DSL.using(SQLDialect.POSTGRES);
            Result<Record1<Integer>> emptyResult = create.newResult(Tables.BOTS.SEARCH_DEPTH);
            return new MockResult[] { new MockResult(0, emptyResult) };
        };

        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
        BotConfigService service = new BotConfigService(dsl);

        Integer depth = service.resolveDepth(BotDifficulty.MEDIUM).block();
        assertEquals(4, depth);
    }
}
