package fpt.qn.junglechess.core.config;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig {

    @Bean
    public DSLContext dslContext(DataSource dataSource) {
        DefaultConfiguration config = new DefaultConfiguration();
        config.set(dataSource);
        config.set(SQLDialect.POSTGRES);
        config.set(new Settings().withReturnAllOnUpdatableRecord(true));
        return DSL.using(config);
    }
}
