package com.quizbot.core.repository;

import com.quizbot.core.domain.Topic;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface TopicRepository extends ReactiveMongoRepository<Topic, String> {
    Mono<Topic> findByName(String name);
    Mono<Boolean> existsByName(String name);
}
