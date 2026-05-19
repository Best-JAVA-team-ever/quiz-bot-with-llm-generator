package com.quizbot.core.service;

import com.quizbot.core.domain.Answers;
import com.quizbot.core.domain.CollectionsName;
import com.quizbot.core.domain.Question;
import com.quizbot.core.repository.AnswersRepository;
import com.quizbot.core.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final AnswersRepository answersRepository;
    private final AuditLogService auditLogService;

    public QuestionServiceImpl(QuestionRepository questionRepository,
            AnswersRepository answersRepository,
            AuditLogService auditLogService) {
        this.questionRepository = questionRepository;
        this.answersRepository = answersRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public Mono<Question> addQuestion(Question question) {
        return create(null, question);
    }

    public Mono<Question> create(String initiatorId, Question question) {
        return questionRepository.save(question)
                .flatMap(saved -> {
                    if (initiatorId == null)
                        return Mono.just(saved);
                    return auditLogService
                            .log(initiatorId, "CREATE_QUESTION",
                                    CollectionsName.QUESTIONS.name(), saved.id())
                            .thenReturn(saved);
                });
    }

    @Override
    public Flux<Question> getAllQuestions() {
        return questionRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public Flux<Question> getQuestionsByTopic(String topicName) {
        return questionRepository.findAllByTopicNamesContainingAndDeletedAtIsNull(topicName);
    }

    @Override
    public Mono<Void> deleteQuestion(String id) {
        return deleteById(null, id);
    }

    public Mono<Void> deleteById(String initiatorId, String questionId) {
        return questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Вопрос с таким ID не существует")))
                .flatMap(q -> questionRepository.save(q.markAsDeleted()))
                .flatMap(saved -> {
                    if (initiatorId == null)
                        return Mono.empty();
                    return auditLogService.log(initiatorId, "DELETE_QUESTION",
                            CollectionsName.QUESTIONS.name(), saved.id());
                });
    }

    @Override
    public Mono<Void> deleteQuestionsByTopic(String topicName) {
        return deleteByTopicId(null, topicName);
    }

    public Mono<Void> deleteByTopicId(String initiatorId, String topicId) {
        return questionRepository.findAllByTopicNamesContainingAndDeletedAtIsNull(topicId)
                .flatMap(q -> questionRepository.save(q.markAsDeleted())
                        .flatMap(saved -> {
                            if (initiatorId == null)
                                return Mono.empty();
                            return auditLogService.log(initiatorId, "DELETE_QUESTION",
                                    CollectionsName.QUESTIONS.name(), saved.id());
                        }))
                .then();
    }

    @Override
    public Mono<Void> deleteAllQuestions() {
        return deleteAll(null);
    }

    public Mono<Void> deleteAll(String initiatorId) {
        return questionRepository.findAllByDeletedAtIsNull()
                .flatMap(q -> questionRepository.save(q.markAsDeleted())
                        .flatMap(saved -> {
                            if (initiatorId == null)
                                return Mono.empty();
                            return auditLogService.log(initiatorId, "DELETE_QUESTION",
                                    CollectionsName.QUESTIONS.name(), saved.id());
                        }))
                .then();
    }

    @Override
    public Mono<Question> updateQuestion(Question updated) {
        return update(null, updated.id(), updated);
    }

    public Mono<Question> update(String initiatorId, String questionId, Question updated) {
        return questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Вопрос с таким ID не существует")))
                .flatMap(existing -> {
                    Question toSave = new Question(
                            existing.id(),
                            updated.text() != null ? updated.text() : existing.text(),
                            updated.correctAnswer() != null ? updated.correctAnswer() : existing.correctAnswer(),
                            updated.wrongAnswers() != null ? updated.wrongAnswers() : existing.wrongAnswers(),
                            updated.difficulty() != 0 ? updated.difficulty() : existing.difficulty(),
                            updated.explanation() != null ? updated.explanation() : existing.explanation(),
                            updated.hint() != null ? updated.hint() : existing.hint(),
                            updated.topicNames() != null ? updated.topicNames() : existing.topicNames(),
                            existing.createdAt(),
                            Instant.now(),
                            existing.deletedAt());
                    return questionRepository.save(toSave);
                })
                .flatMap(saved -> {
                    if (initiatorId == null)
                        return Mono.just(saved);
                    return auditLogService
                            .log(initiatorId, "UPDATE_QUESTION",
                                    CollectionsName.QUESTIONS.name(), saved.id())
                            .thenReturn(saved);
                });
    }

    @Override
    public Mono<Question> getQuestionById(String id) {
        return questionRepository.findByIdAndDeletedAtIsNull(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Вопрос с таким ID не существует")));
    }

    public Flux<Question> findByTopicId(String topicId) {
        return questionRepository.findAllByTopicNamesContainingAndDeletedAtIsNull(topicId);
    }

    public Mono<Long> countByTopicId(String topicId) {
        return questionRepository.countByTopicNamesContainingAndDeletedAtIsNull(topicId);
    }

    public Mono<Question> pickRandom(String userId, Instant scoreResetAt, List<String> topicNames) {
        Flux<Question> pool = (topicNames == null || topicNames.isEmpty())
                ? questionRepository.findAllByDeletedAtIsNull()
                : questionRepository.findAllByTopicNamesInAndDeletedAtIsNull(topicNames);

        Instant since = scoreResetAt != null ? scoreResetAt : Instant.EPOCH;

        return answersRepository.findAllByUserIdAndAnsweredAtAfter(userId, since)
                .map(Answers::questionId)
                .collect(Collectors.toSet())
                .flatMap(answeredIds -> pool
                        .filter(q -> !answeredIds.contains(q.id()))
                        .collectList()
                        .flatMap(unanswered -> {
                            if (unanswered.isEmpty())
                                return Mono.empty();
                            Collections.shuffle(unanswered);
                            return Mono.just(unanswered.get(0));
                        }));
    }

    public Flux<Question> findAllForDifficultyUpdate() {
        return questionRepository.findAllByDeletedAtIsNull();
    }
}