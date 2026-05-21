package com.quizbot.core.repository;

import com.quizbot.core.domain.Question;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

@Repository
public interface QuestionRepository extends ReactiveMongoRepository<Question, String> {

    // /update question <ID>, /delete question <ID> — поиск вопроса по ID
    Mono<Question> findByIdAndDeletedAtIsNull(String id);

    // /get questions all, /delete question all, /update difficulty — все
    // неудалённые вопросы
    Flux<Question> findAllByDeletedAtIsNull();

    // /get questions <topic>, /delete question <topic>, /quiz start <topic>
    // — вопросы по одному тегу
    Flux<Question> findAllByTopicNamesContainingAndDeletedAtIsNull(String topicName);

    // /quiz start <topic1> <topic2> ... — вопросы по нескольким тегам сразу
    Flux<Question> findAllByTopicNamesInAndDeletedAtIsNull(Collection<String> topicNames);

    // \get questions (Admin/User) — подсчёт вопросов по теме
    Mono<Long> countByTopicNamesContainingAndDeletedAtIsNull(String topicName);

    // Проверка наличия вопросов по теме перед \delete topic — нельзя удалить тему с
    // вопросами
    Mono<Boolean> existsByTopicNamesContainingAndDeletedAtIsNull(String topicName);
}
