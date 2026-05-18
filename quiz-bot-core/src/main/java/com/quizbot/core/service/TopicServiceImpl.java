package com.quizbot.core.service;

import com.quizbot.core.domain.Topic;
import com.quizbot.core.repository.TopicRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TopicServiceImpl implements TopicService {
    private final TopicRepository topicRepository;

    public TopicServiceImpl(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    @Override
    public Mono<Topic> addTopic(String name) {
        if (!isValid(name)) {
            return Mono.error(new IllegalArgumentException("Тема " + name + " некорректна"));
        }
        return exists(name).flatMap(exists -> {
            if (exists) {
                return Mono.error(new IllegalArgumentException("Тема " + name + " уже существует"));
            }
            return topicRepository.save(new Topic(null, name));
        });
    }

    @Override
    public Flux<Topic> getAllTopics() {
        return topicRepository.findAll();
    }

    @Override
    public Mono<Void> deleteTopic(String name) {
        return topicRepository.findByName(name)
                .flatMap(topic -> topicRepository.delete(topic));
    }

    @Override
    public Mono<Boolean> exists(String name) {
        return topicRepository.existsByName(name);
    }

    @Override
    public boolean isValid(String name) {
        return name != null && !name.equalsIgnoreCase("all") && 
               name.matches("^[а-яА-Яa-zA-Z0-9\\s\\.,!?;:\\-\"\\'()]{2,32}$");
    }
}
