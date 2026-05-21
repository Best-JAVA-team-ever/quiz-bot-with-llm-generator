package com.quizbot.core.service;

import com.quizbot.core.domain.Answers;
import com.quizbot.core.domain.Question;
import com.quizbot.core.repository.AnswersRepository;
import com.quizbot.core.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuizServiceImpl implements QuizService {

        private final QuestionRepository questionRepository;
        private final AnswersRepository answersRepository;
        private final UserService userService;

        public QuizServiceImpl(QuestionRepository questionRepository,
                        AnswersRepository answersRepository,
                        UserService userService) {
                this.questionRepository = questionRepository;
                this.answersRepository = answersRepository;
                this.userService = userService;
        }

        @Override
        public Mono<Question> startQuiz(Long userId, List<String> topics) {
                String userIdStr = String.valueOf(userId);

                Flux<Question> questionsFlux = (topics == null || topics.isEmpty())
                                ? questionRepository.findAllByDeletedAtIsNull()
                                : questionRepository.findAllByTopicNamesInAndDeletedAtIsNull(topics);

                return userService.findByTelegramId(userId)
                                .onErrorResume(e -> userService.getOrCreateUser(userId))
                                .flatMap(user -> {
                                        Instant since = user.scoreResetAt() != null
                                                        ? user.scoreResetAt()
                                                        : Instant.EPOCH;

                                        return questionsFlux.collectList()
                                                        .flatMap(allQuestions -> {
                                                                if (allQuestions.isEmpty())
                                                                        return Mono.empty();

                                                                return answersRepository
                                                                                .findAllByUserIdAndAnsweredAtAfter(userIdStr, since)
                                                                                .collectList()
                                                                                .flatMap(userAnswers ->
                                                                                        pickNext(allQuestions, userAnswers)
                                                                                                .map(Mono::just)
                                                                                                .orElse(Mono.empty()));
                                                        });
                                });
        }

        // package-private for unit testing
        static java.util.Optional<Question> pickNext(List<Question> allQuestions, List<Answers> userAnswers) {
                List<String> answeredCorrectlyIds = userAnswers.stream()
                                .filter(Answers::isCorrect)
                                .map(Answers::questionId)
                                .collect(Collectors.toList());

                java.util.Map<String, Instant> lastAnsweredMap = userAnswers.stream()
                                .collect(Collectors.toMap(Answers::questionId, Answers::answeredAt,
                                                (a, b) -> a.isAfter(b) ? a : b));

                List<Question> available = allQuestions.stream()
                                .filter(q -> !answeredCorrectlyIds.contains(q.id()))
                                .sorted(Comparator.comparingInt(Question::difficulty)
                                                .thenComparing(q -> lastAnsweredMap.getOrDefault(q.id(), Instant.EPOCH)))
                                .collect(Collectors.toList());

                if (available.isEmpty()) return java.util.Optional.empty();

                List<Question> candidates = new ArrayList<>(available.subList(0, Math.min(3, available.size())));
                Collections.shuffle(candidates);
                return java.util.Optional.of(candidates.get(0));
        }

        @Override
        public Mono<Void> recordAnswer(Long userId, String questionId, String value, boolean isCorrect) {
                Answers answers = Answers.create(String.valueOf(userId), questionId, value, isCorrect);
                return answersRepository.save(answers).then();
        }
}