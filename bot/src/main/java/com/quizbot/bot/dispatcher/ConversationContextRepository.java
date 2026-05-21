package com.quizbot.bot.dispatcher;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationContextRepository extends ReactiveMongoRepository<ConversationContext, Long> {
}
