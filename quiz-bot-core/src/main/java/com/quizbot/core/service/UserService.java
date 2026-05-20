package com.quizbot.core.service;

import com.quizbot.core.domain.Role;
import com.quizbot.core.domain.Users;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserService {
    Mono<Users> getOrCreateUser(Long telegramId);

    Mono<Void> upgradeUser(Long telegramId);

    Flux<Users> getAllUsers();

    Flux<Users> findAllActive();

    Mono<Role> getRole(Long telegramId);
}
