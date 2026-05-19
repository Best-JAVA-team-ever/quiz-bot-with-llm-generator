package com.quizbot.core.service;

import com.quizbot.core.domain.Topic;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TopicService {
    Mono<Topic> addTopic(String name);

    Flux<Topic> getAllTopics();

    Mono<Void> deleteTopic(String name);

    Mono<Topic> rename(String initiatorId, String topicId, String newName);

    Mono<Topic> findOrCreate(String initiatorId, String name);

    Mono<Boolean> exists(String name);

    boolean isValid(String name);
}
