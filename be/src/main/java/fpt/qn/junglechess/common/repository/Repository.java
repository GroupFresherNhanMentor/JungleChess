package fpt.qn.junglechess.common.repository;

import java.util.UUID;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Repository<R> {

    Mono<R> findById(UUID id);

    Flux<R> findAll();

    Mono<R> create(R record);

    Mono<R> update(R record);

    Mono<Void> hardDeleteById(UUID id);

    Mono<Boolean> existsById(UUID id);
}
