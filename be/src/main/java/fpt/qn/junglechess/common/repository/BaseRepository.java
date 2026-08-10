package fpt.qn.junglechess.common.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.UpdatableRecord;

public abstract class BaseRepository<R extends UpdatableRecord<R>> implements Repository<R> {

    protected final DSLContext dsl;
    protected final Table<R> table;

    protected BaseRepository(DSLContext dsl, Table<R> table) {
        this.dsl = dsl;
        this.table = table;
    }

    @Override
    public Optional<R> findById(UUID id) {
        return dsl.selectFrom(table)
                .where(table.field("id", UUID.class).eq(id))
                .fetchOptional();
    }

    @Override
    public List<R> findAll() {
        return dsl.selectFrom(table).fetch();
    }

    @Override
    public R create(R record) {
        return dsl.insertInto(table).set(record).returning().fetchOne();
    }

    @Override
    public R update(R record) {
        return dsl.update(table)
                .set(record)
                .where(table.field("id", UUID.class).eq((UUID) record.get("id")))
                .returning()
                .fetchOne();
    }

    @Override
    public void hardDeleteById(UUID id) {
        dsl.deleteFrom(table)
                .where(table.field("id", UUID.class).eq(id))
                .execute();
    }

    @Override
    public boolean existsById(UUID id) {
        return dsl.fetchExists(
                dsl.selectFrom(table).where(table.field("id", UUID.class).eq(id))
        );
    }
}
