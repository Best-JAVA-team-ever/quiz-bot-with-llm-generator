package com.quizbot.api.dispatcher;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ConversationContextRepository extends ReactiveMongoRepository<ConversationContext, Long> {
}
