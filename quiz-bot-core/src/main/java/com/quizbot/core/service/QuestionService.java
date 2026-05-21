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
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final AnswersRepository answersRepository;
    private final AuditLogService auditLogService;

    public QuestionService(QuestionRepository questionRepository,
            AnswersRepository answersRepository,
            AuditLogService auditLogService) {
        this.questionRepository = questionRepository;
        this.answersRepository = answersRepository;
        this.auditLogService = auditLogService;
    }

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

    public Flux<Question> getAllQuestions() {
        return questionRepository.findAllByDeletedAtIsNull();
    }

    public Flux<Question> getQuestionsByTopic(String topicName) {
        return questionRepository.findAllByTopicNamesContainingAndDeletedAtIsNull(topicName);
    }

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

        return pool.collectList().flatMap(allQuestions -> {
            if (allQuestions.isEmpty()) return Mono.empty();
            return answersRepository.findAllByUserIdAndAnsweredAtAfter(userId, since)
                .collectList()
                .flatMap(userAnswers -> {
                    java.util.List<String> answeredCorrectlyIds = userAnswers.stream()
                            .filter(Answers::isCorrect)
                            .map(Answers::questionId)
                            .collect(Collectors.toList());

                    java.util.Map<String, Instant> lastAnsweredMap = userAnswers.stream()
                            .collect(Collectors.toMap(Answers::questionId, Answers::answeredAt, (a, b) -> a.isAfter(b) ? a : b));

                    java.util.List<Question> available = allQuestions.stream()
                            .filter(q -> !answeredCorrectlyIds.contains(q.id()))
                            .sorted(java.util.Comparator.comparingInt(Question::difficulty)
                                    .thenComparing(q -> lastAnsweredMap.getOrDefault(q.id(), Instant.EPOCH)))
                            .collect(Collectors.toList());

                    if (available.isEmpty()) return Mono.empty();

                    int shuffleRange = Math.min(3, available.size());
                    java.util.List<Question> candidates = new java.util.ArrayList<>(available.subList(0, shuffleRange));
                    Collections.shuffle(candidates);
                    return Mono.just(candidates.get(0));
                });
        });
    }

    public Flux<Question> findAllForDifficultyUpdate() {
        return questionRepository.findAllByDeletedAtIsNull();
    }
}