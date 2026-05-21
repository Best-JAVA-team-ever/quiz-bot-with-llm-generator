package com.quizbot.core.repository;

import com.quizbot.core.domain.Answers;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Collection;

@Repository
public interface AnswersRepository extends ReactiveMongoRepository<Answers, String> {

        // Логика викторины §2.3.2 — все ответы пользователя после сброса счёта.
        // Используется для исключения уже отвеченных вопросов перед случайной выборкой.
        Flux<Answers> findAllByUserIdAndAnsweredAtAfter(String userId, Instant after);

        // \score — статистика пользователя с учётом даты сброса
        // (переиспользует findAllByUserIdAndAnsweredAtAfter)

        // \score <topic> — статистика пользователя по теме с учётом даты сброса
        Flux<Answers> findAllByUserIdAndQuestionIdInAndAnsweredAtAfter(String userId,
                        Collection<String> questionIds,
                        Instant after);

        // /group score (Admin/User) — статистика участников группы
        Flux<Answers> findAllByUserIdIn(Collection<String> userIds);

        // /group score по теме — ответы участников группы только по нужным вопросам
        Flux<Answers> findAllByUserIdInAndQuestionIdIn(Collection<String> userIds,
                        Collection<String> questionIds);

        // /update difficulty — все ответы по вопросам для передачи статистики в LLM
        Flux<Answers> findAllByQuestionIdIn(Collection<String> questionIds);
}
