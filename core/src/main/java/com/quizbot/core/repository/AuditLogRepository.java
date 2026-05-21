package com.quizbot.core.repository;

import com.quizbot.core.domain.AuditLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface AuditLogRepository extends ReactiveMongoRepository<AuditLog, String> {

    // Запись всегда создаётся через унаследованный save() — дополнительных
    // write-методов не нужно.

    // История изменений конкретной сущности (например, вопроса или тега) —
    // по составному индексу targetCollection + targetId
    Flux<AuditLog> findAllByTargetCollectionAndTargetIdOrderByCreatedAtDesc(
            String targetCollection, String targetId);

    // История всех действий конкретного администратора
    Flux<AuditLog> findAllByInitiatorIdOrderByCreatedAtDesc(String initiatorId);
}
