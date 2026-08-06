package fpt.qn.junglechess.core.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.r2dbc.spi.ConnectionFactory;

@Configuration
public class JooqConfig {

    @Bean
    public DSLContext dslContext(ConnectionFactory connectionFactory) {
        DefaultConfiguration config = new DefaultConfiguration();
        config.set(connectionFactory);
        config.set(SQLDialect.POSTGRES);
        config.set(new Settings().withReturnAllOnUpdatableRecord(true));
        return DSL.using(config);
    }
}
