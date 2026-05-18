package com.quizbot.core.service;

import com.quizbot.core.domain.User;
import com.quizbot.core.domain.UserRole;
import com.quizbot.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final Set<Long> initialAdminIds;

    public UserServiceImpl(UserRepository userRepository, 
                           @Value("${admin.chat.ids:}") String adminIds) {
        this.userRepository = userRepository;
        this.initialAdminIds = Arrays.stream(adminIds.split(","))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    @Override
    public Mono<User> getOrCreateUser(Long telegramId) {
        return userRepository.findById(telegramId)
                .switchIfEmpty(Mono.defer(() -> {
                    UserRole role = initialAdminIds.contains(telegramId) ? UserRole.ADMIN : UserRole.USER;
                    return userRepository.save(new User(telegramId, role, LocalDateTime.now()));
                }));
    }

    @Override
    public Mono<Void> upgradeUser(Long telegramId) {
        return userRepository.findById(telegramId)
                .flatMap(user -> userRepository.save(new User(user.telegramId(), UserRole.ADMIN, user.registrationDate())))
                .then();
    }

    @Override
    public Flux<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public Mono<UserRole> getRole(Long telegramId) {
        return getOrCreateUser(telegramId).map(User::role);
    }
}
