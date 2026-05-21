package com.quizbot.core.service;

import com.quizbot.core.domain.Answers;
import com.quizbot.core.domain.Question;
import com.quizbot.core.llm.LlmClient;
import com.quizbot.core.repository.AnswersRepository;
import com.quizbot.core.repository.GroupMemberRepository;
import com.quizbot.core.repository.GroupRepository;
import com.quizbot.core.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatisticsService {

    private final AnswersRepository answersRepository;
    private final QuestionRepository questionRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserService userService;

    public StatisticsService(AnswersRepository answersRepository,
            QuestionRepository questionRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserService userService) {
        this.answersRepository = answersRepository;
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userService = userService;
    }

    public Mono<Map<String, Object>> getUserStats(Long userId, String topicName) {
        String userIdStr = String.valueOf(userId);

        if (topicName != null && !topicName.isEmpty()) {
            return userService.findByTelegramId(userId).flatMap(user ->
                questionRepository
                    .findAllByTopicNamesContainingAndDeletedAtIsNull(topicName)
                    .map(Question::id)
                    .collectList()
                    .flatMap(questionIds -> answersRepository
                        .findAllByUserIdAndQuestionIdInAndAnsweredAtAfter(
                            userIdStr,
                            questionIds,
                            user.scoreResetAt() != null ? user.scoreResetAt() : Instant.EPOCH)
                        .collectList()
                        .map(StatisticsService::calculateStats)));
        }

        return userService.findByTelegramId(userId).flatMap(user ->
            answersRepository
                .findAllByUserIdAndAnsweredAtAfter(
                    userIdStr,
                    user.scoreResetAt() != null ? user.scoreResetAt() : Instant.EPOCH)
                .collectList()
                .map(StatisticsService::calculateStats));
    }

    public Mono<Void> resetUserStats(Long userId) {
        return userService.resetScore(String.valueOf(userId)).then();
    }

    public Mono<Map<String, Object>> getGroupStats(String groupId) {
        return groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .flatMap(group -> groupMemberRepository.findAllByGroupIdAndDeletedAtIsNull(groupId)
                        .map(member -> String.valueOf(member.userId()))
                        .collectList()
                        .flatMap(userIds -> {
                            if (userIds.isEmpty()) {
                                return Mono.just(calculateStats(List.of()));
                            }
                            return answersRepository.findAllByUserIdIn(userIds)
                                    .collectList()
                                    .map(StatisticsService::calculateStats);
                        }))
                .defaultIfEmpty(new HashMap<>());
    }

    public Flux<LlmClient.QuestionWithStats> getQuestionsWithStats() {
        Mono<List<Answers>> allAnswersMono = answersRepository.findAll().collectList();
        Flux<Question> allQuestionsFlux = questionRepository.findAllByDeletedAtIsNull();

        return allQuestionsFlux.flatMap(q -> allAnswersMono.map(allAnswers -> {
            long total = allAnswers.stream()
                    .filter(a -> a.questionId().equals(q.id())).count();
            long correct = allAnswers.stream()
                    .filter(a -> a.questionId().equals(q.id()) && a.isCorrect()).count();
            return new LlmClient.QuestionWithStats(q, correct, total);
        }));
    }

    public Mono<Long> countCorrectByUserAndTopic(String userId, String topicName) {
        return userService.findByTelegramId(Long.parseLong(userId)).flatMap(user ->
            questionRepository
                .findAllByTopicNamesContainingAndDeletedAtIsNull(topicName)
                .map(Question::id)
                .collectList()
                .flatMap(questionIds -> answersRepository
                    .findAllByUserIdAndQuestionIdInAndAnsweredAtAfter(
                        userId,
                        questionIds,
                        user.scoreResetAt() != null ? user.scoreResetAt() : Instant.EPOCH)
                    .filter(Answers::isCorrect)
                    .count()));
    }

    static Map<String, Object> calculateStats(List<Answers> answers) {
        long total = answers.size();
        long correct = answers.stream().filter(Answers::isCorrect).count();
        long incorrect = total - correct;
        double percentage = total == 0 ? 0.0 : (double) correct / total * 100;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("correct", correct);
        stats.put("incorrect", incorrect);
        stats.put("percentage", Math.round(percentage * 10.0) / 10.0);
        return stats;
    }
}