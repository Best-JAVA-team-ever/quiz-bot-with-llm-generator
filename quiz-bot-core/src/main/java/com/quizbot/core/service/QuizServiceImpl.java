package com.quizbot.core.service;

import com.quizbot.core.domain.Answer;
import com.quizbot.core.domain.Question;
import com.quizbot.core.repository.AnswerRepository;
import com.quizbot.core.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuizServiceImpl implements QuizService {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    public QuizServiceImpl(QuestionRepository questionRepository, AnswerRepository answerRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    @Override
    public Mono<Question> startQuiz(Long userId, List<String> topics) {
        Flux<Question> questionsFlux;
        if (topics == null || topics.isEmpty()) {
            questionsFlux = questionRepository.findAll();
        } else {
            questionsFlux = Flux.fromIterable(topics)
                    .flatMap(questionRepository::findByTopicNamesContaining)
                    .distinct();
        }

        return questionsFlux.collectList().flatMap(allQuestions -> {
            if (allQuestions.isEmpty()) return Mono.empty();

            return answerRepository.findByUserId(userId).collectList().flatMap(userAnswers -> {
                List<String> correctIds = userAnswers.stream()
                        .filter(Answer::isCorrect)
                        .map(Answer::questionId)
                        .collect(Collectors.toList());

                List<Question> available = allQuestions.stream()
                        .filter(q -> !correctIds.contains(q.id()))
                        .collect(Collectors.toList());

                if (available.isEmpty()) return Mono.empty();

                available.sort(Comparator.comparing(Question::difficulty)
                        .thenComparing(q -> getLastAnswerDate(q.id(), userAnswers)));

                int shuffleRange = Math.min(3, available.size());
                List<Question> candidates = new ArrayList<>(available.subList(0, shuffleRange));
                Collections.shuffle(candidates);

                return Mono.just(candidates.get(0));
            });
        });
    }

    private LocalDateTime getLastAnswerDate(String questionId, List<Answer> answers) {
        return answers.stream()
                .filter(a -> a.questionId().equals(questionId))
                .map(Answer::timestamp)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.MIN);
    }

    @Override
    public Mono<Boolean> processAnswer(Long userId, String answer) {
        return Mono.just(false);
    }
    
    @Override
    public Mono<Void> recordAnswer(Long userId, String questionId, String value, boolean isCorrect) {
        return answerRepository.save(new Answer(null, questionId, userId, value, isCorrect, LocalDateTime.now())).then();
    }
}
