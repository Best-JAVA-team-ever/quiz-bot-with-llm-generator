package com.quizbot.core.service;

import com.quizbot.core.domain.Answers;
import com.quizbot.core.repository.AnswersRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;

@Service
public class AnswerService {

    private final AnswersRepository answersRepository;

    public AnswerService(AnswersRepository answersRepository) {
        this.answersRepository = answersRepository;
    }

    public Mono<Answers> record(String userId, String questionId,
                                String chosenAnswer, boolean isCorrect) {
        Answers answer = Answers.create(userId, questionId, chosenAnswer, isCorrect);
        return answersRepository.save(answer);
    }

    public Flux<Answers> findAllByUser(String userId, Instant scoreResetAt) {
        Instant since = scoreResetAt != null ? scoreResetAt : Instant.EPOCH;
        return answersRepository.findAllByUserIdAndAnsweredAtAfter(userId, since);
    }

    public Flux<Answers> findByUserAndQuestions(String userId, Instant scoreResetAt,
                                                Collection<String> questionIds) {
        Instant since = scoreResetAt != null ? scoreResetAt : Instant.EPOCH;
        return answersRepository.findAllByUserIdAndQuestionIdInAndAnsweredAtAfter(
                userId, questionIds, since);
    }

    public Flux<Answers> findAllByUsers(Collection<String> userIds) {
        return answersRepository.findAllByUserIdIn(userIds);
    }

    public Flux<Answers> findByUsersAndQuestions(Collection<String> userIds,
                                                  Collection<String> questionIds) {
        return answersRepository.findAllByUserIdInAndQuestionIdIn(userIds, questionIds);
    }

    public Flux<Answers> findAllByQuestions(Collection<String> questionIds) {
        return answersRepository.findAllByQuestionIdIn(questionIds);
    }
}
