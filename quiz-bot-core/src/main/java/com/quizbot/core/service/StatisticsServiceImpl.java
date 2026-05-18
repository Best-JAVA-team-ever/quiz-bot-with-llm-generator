package com.quizbot.core.service;

import com.quizbot.core.domain.Answer;
import com.quizbot.core.domain.Question;
import com.quizbot.core.repository.AnswerRepository;
import com.quizbot.core.repository.QuestionRepository;
import com.quizbot.core.repository.GroupRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final GroupRepository groupRepository;

    public StatisticsServiceImpl(AnswerRepository answerRepository, QuestionRepository questionRepository, GroupRepository groupRepository) {
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    public Mono<Map<String, Object>> getUserStats(Long userId, String topicName) {
        Mono<List<Answer>> answersMono = answerRepository.findByUserId(userId).collectList();
        
        if (topicName != null && !topicName.isEmpty()) {
            Mono<List<String>> questionIdsInTopicMono = questionRepository.findByTopicNamesContaining(topicName)
                    .map(Question::id)
                    .collectList();
            
            return answersMono.zipWith(questionIdsInTopicMono, (answers, questionIdsInTopic) -> {
                List<Answer> filtered = answers.stream()
                        .filter(a -> questionIdsInTopic.contains(a.questionId()))
                        .collect(Collectors.toList());
                return calculateStats(filtered);
            });
        }

        return answersMono.map(this::calculateStats);
    }

    @Override
    public Mono<Void> resetUserStats(Long userId) {
        return answerRepository.deleteByUserId(userId);
    }

    @Override
    public Mono<Map<String, Object>> getGroupStats(String groupId) {
        return groupRepository.findById(groupId)
                .flatMap(group -> Flux.fromIterable(group.memberIds())
                        .flatMap(answerRepository::findByUserId)
                        .collectList()
                        .map(this::calculateStats))
                .defaultIfEmpty(new HashMap<>());
    }

    @Override
    public Flux<com.quizbot.core.llm.LlmClient.QuestionWithStats> getQuestionsWithStats() {
        Mono<List<Answer>> allAnswersMono = answerRepository.findAll().collectList();
        Flux<Question> allQuestionsFlux = questionRepository.findAll();

        return allQuestionsFlux.flatMap(q -> allAnswersMono.map(allAnswers -> {
            long total = allAnswers.stream().filter(a -> a.questionId().equals(q.id())).count();
            long correct = allAnswers.stream().filter(a -> a.questionId().equals(q.id()) && a.isCorrect()).count();
            return new com.quizbot.core.llm.LlmClient.QuestionWithStats(q, correct, total);
        }));
    }

    private Map<String, Object> calculateStats(List<Answer> answers) {
        long total = answers.size();
        long correct = answers.stream().filter(Answer::isCorrect).count();
        long incorrect = total - correct;
        double percentage = total == 0 ? 0 : (double) correct / total * 100;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("correct", correct);
        stats.put("incorrect", incorrect);
        stats.put("percentage", Math.round(percentage * 10.0) / 10.0);
        return stats;
    }
}
