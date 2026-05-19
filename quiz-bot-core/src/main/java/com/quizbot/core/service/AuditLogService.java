package com.quizbot.core.service;

import com.quizbot.core.domain.AuditLog;
import com.quizbot.core.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public Mono<Void> log(String initiatorId, String action,
                          String targetCollection, String targetId) {
        AuditLog entry = AuditLog.create(initiatorId, action, targetCollection, targetId);
        return auditLogRepository.save(entry).then();
    }

    public Flux<AuditLog> findByTarget(String collection, String targetId) {
        return auditLogRepository
                .findAllByTargetCollectionAndTargetIdOrderByCreatedAtDesc(collection, targetId);
    }

    public Flux<AuditLog> findByInitiator(String initiatorId) {
        return auditLogRepository.findAllByInitiatorIdOrderByCreatedAtDesc(initiatorId);
    }
}
