package com.quizbot.core.repository;

import com.quizbot.core.domain.Answer;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface AnswerRepository extends ReactiveMongoRepository<Answer, String> {
    Flux<Answer> findByUserId(Long userId);
    Flux<Answer> findByUserIdAndIsCorrectTrue(Long userId);
    Mono<Void> deleteByUserId(Long userId);
}
