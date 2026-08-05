package fpt.qn.junglechess.common.repository;

import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.UpdatableRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class BaseRepository<R extends UpdatableRecord<R>> implements Repository<R> {

    protected final DSLContext dsl;
    protected final Table<R> table;

    protected BaseRepository(DSLContext dsl, Table<R> table) {
        this.dsl = dsl;
        this.table = table;
    }

    public Optional<R> findById(UUID id) {
        return dsl.selectFrom(table)
                .where(table.field("id", UUID.class).eq(id))
                .fetchOptional();
    }

    public Optional<R> findByIdForUpdate(UUID id) {
        return dsl.selectFrom(table)
                .where(table.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional();
    }

    public List<R> findAll() {
        return dsl.selectFrom(table).fetch();
    }

    public R update(R record) {
        record.update();
        return record;
    }

    public R create(R record) {
        return dsl.insertInto(table)
                .set(record)
                .returning()
                .fetchOne();
    }

    public void hardDeleteById(UUID id) {
        dsl.deleteFrom(table)
                .where(table.field("id", UUID.class).eq(id))
                .execute();
    }

    public boolean existsById(UUID id) {
        return dsl.fetchExists(
                dsl.selectFrom(table)
                        .where(table.field("id", UUID.class).eq(id)));
    }
}
