package com.quizbot.core.repository;

import com.quizbot.core.domain.MetricsSnapshot;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface MetricsSnapshotRepository extends ReactiveMongoRepository<MetricsSnapshot, String> {
    Mono<MetricsSnapshot> findTopByOrderBySavedAtDesc();
}
