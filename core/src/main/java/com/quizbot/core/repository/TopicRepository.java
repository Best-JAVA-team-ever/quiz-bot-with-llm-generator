package com.quizbot.core.repository;

import com.quizbot.core.domain.Topic;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

@Repository
public interface TopicRepository extends ReactiveMongoRepository<Topic, String> {

    // Проверка уникальности при \add topic, поиск темы по названию для команд \get
    // /
    // \delete / \quiz
    Mono<Topic> findByNameAndDeletedAtIsNull(String name);

    // Список всех активных тем для \get questions
    Flux<Topic> findAllByDeletedAtIsNull();

    // Получить несколько тем по списку ID — отображение тем у вопросов
    Flux<Topic> findAllByIdInAndDeletedAtIsNull(Collection<String> ids);

    // Поиск по ID для /update topic и /delete topic
    Mono<Topic> findByIdAndDeletedAtIsNull(String id);
}
