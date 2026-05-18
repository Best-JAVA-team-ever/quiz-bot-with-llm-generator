package com.quizbot.core.service;

import com.quizbot.core.domain.User;
import com.quizbot.core.domain.UserRole;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserService {
    Mono<User> getOrCreateUser(Long telegramId);
    Mono<Void> upgradeUser(Long telegramId);
    Flux<User> getAllUsers();
    Mono<UserRole> getRole(Long telegramId);
}
