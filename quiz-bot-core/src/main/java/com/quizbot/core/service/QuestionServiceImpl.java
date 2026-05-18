package com.quizbot.core.service;

import com.quizbot.core.domain.Question;
import com.quizbot.core.domain.Topic;
import com.quizbot.core.repository.QuestionRepository;
import com.quizbot.core.repository.TopicRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class QuestionServiceImpl implements QuestionService {
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;

    public QuestionServiceImpl(QuestionRepository questionRepository, TopicRepository topicRepository) {
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
    }

    @Override
    public Mono<Question> addQuestion(Question question) {
        if (question.topicNames() == null || question.topicNames().isEmpty()) {
            return questionRepository.save(question);
        }

        return Flux.fromIterable(question.topicNames())
                .flatMap(topicName -> topicRepository.existsByName(topicName)
                        .flatMap(exists -> {
                            if (!exists) {
                                return topicRepository.save(new Topic(null, topicName));
                            }
                            return Mono.empty();
                        }))
                .then(questionRepository.save(question));
    }

    @Override
    public Flux<Question> getAllQuestions() {
        return questionRepository.findAll();
    }

    @Override
    public Flux<Question> getQuestionsByTopic(String topicName) {
        return questionRepository.findByTopicNamesContaining(topicName);
    }

    @Override
    public Mono<Void> deleteQuestion(String id) {
        return questionRepository.deleteById(id);
    }

    @Override
    public Mono<Void> deleteQuestionsByTopic(String topicName) {
        return questionRepository.deleteByTopicNamesContaining(topicName);
    }

    @Override
    public Mono<Void> deleteAllQuestions() {
        return questionRepository.deleteAll();
    }

    @Override
    public Mono<Question> updateQuestion(Question question) {
        return questionRepository.save(question);
    }

    @Override
    public Mono<Question> getQuestionById(String id) {
        return questionRepository.findById(id);
    }
}
