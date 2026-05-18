package com.quizbot.core.repository;

import com.quizbot.core.domain.Question;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface QuestionRepository extends ReactiveMongoRepository<Question, String> {
    Flux<Question> findByTopicNamesContaining(String topicName);
    Mono<Long> countByTopicNamesContaining(String topicName);
    Mono<Void> deleteByTopicNamesContaining(String topicName);
}
