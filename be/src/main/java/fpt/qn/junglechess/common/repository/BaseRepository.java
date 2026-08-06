package fpt.qn.junglechess.common.repository;

import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.UpdatableRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseRepository<R extends UpdatableRecord<R>> implements Repository<R> {

    protected final DSLContext dsl;
    protected final Table<R> table;

    protected BaseRepository(DSLContext dsl, Table<R> table) {
        this.dsl = dsl;
        this.table = table;
    }

    @Override
    public Mono<R> findById(UUID id) {
        return Mono.from(
            dsl.selectFrom(table)
                .where(table.field("id", UUID.class).eq(id))
        );
    }

    @Override
    public Flux<R> findAll() {
        return Flux.from(dsl.selectFrom(table));
    }

    @Override
    public Mono<R> create(R record) {
        return Mono.from(
            dsl.insertInto(table)
                .set(record)
                .returning()
        );
    }

    @Override
    public Mono<R> update(R record) {
        return Mono.from(
            dsl.update(table)
                .set(record)
                .where(table.field("id", UUID.class).eq((UUID) record.get("id")))
                .returning()
        );
    }

    @Override
    public Mono<Void> hardDeleteById(UUID id) {
        return Mono.from(
            dsl.deleteFrom(table)
                .where(table.field("id", UUID.class).eq(id))
        ).then();
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return Mono.from(
            dsl.selectOne()
                .whereExists(dsl.selectFrom(table).where(table.field("id", UUID.class).eq(id)))
        ).map(r -> true).defaultIfEmpty(false);
    }
}
