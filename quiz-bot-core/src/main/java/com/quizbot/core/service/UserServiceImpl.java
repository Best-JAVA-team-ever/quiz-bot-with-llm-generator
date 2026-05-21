package com.quizbot.core.service;

import com.quizbot.core.domain.Role;
import com.quizbot.core.domain.Users;
import com.quizbot.core.repository.UsersRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UsersRepository usersRepository;
    private final AuditLogService auditLogService;
    private final Set<Long> initialAdminIds;

    public UserServiceImpl(UsersRepository usersRepository,
            AuditLogService auditLogService,
            @Value("${admin.chat.ids:}") String adminIds) {
        this.usersRepository = usersRepository;
        this.auditLogService = auditLogService;
        this.initialAdminIds = Arrays.stream(adminIds.split(","))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    @Override
    public Mono<Users> getOrCreateUser(Long telegramId) {
        return usersRepository.findByTelegramIdAndDeletedAtIsNull(telegramId)
                .switchIfEmpty(Mono.defer(() -> {
                    Users newUser = Users.create(telegramId, (initialAdminIds.contains(telegramId) ? Role.ADMIN : Role.USER));
                    return usersRepository.save(newUser);
                }));
    }

    @Override
    public Mono<Void> upgradeUser(Long telegramId) {
        return usersRepository.findByTelegramId(telegramId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Пользователь не найден")))
                .flatMap(user -> {
                    Users upgraded = user.withRole(Role.ADMIN);
                    return usersRepository.save(upgraded);
                })
                .flatMap(user -> auditLogService
                        .log(String.valueOf(telegramId), "UPGRADE_TO_ADMIN",
                                "users", user.id()))
                .then();
    }

    @Override
    public Flux<Users> getAllUsers() {
        return usersRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public Mono<Role> getRole(Long telegramId) {
        return getOrCreateUser(telegramId)
                .map(u -> u.role() == Role.ADMIN ? Role.ADMIN : Role.USER);
    }

    // --- дополнительные методы, используемые внутри модуля ---

    public Mono<Users> upgradeToAdmin(String initiatorId, String targetUserId) {
        return usersRepository.findByIdAndDeletedAtIsNull(targetUserId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Пользователь не найден")))
                .flatMap(user -> {
                    Users upgraded = user.withRole(Role.ADMIN);
                    return usersRepository.save(upgraded);
                })
                .flatMap(user -> auditLogService
                        .log(initiatorId, "UPGRADE_TO_ADMIN", "users", user.id())
                        .thenReturn(user));
    }

    public Mono<Users> resetScore(String userId) {
        return usersRepository.findByTelegramIdAndDeletedAtIsNull(Long.parseLong(userId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Пользователь не найден")))
                .flatMap(user -> {
                    Users updated = user.withScoreResetAt(Instant.now());
                    return usersRepository.save(updated);
                });
    }

    public Mono<Users> findById(String userId) {
        return usersRepository.findByIdAndDeletedAtIsNull(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Пользователь не найден")));
    }

    public Mono<Users> findByTelegramId(Long telegramId) {
        return usersRepository.findByTelegramIdAndDeletedAtIsNull(telegramId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Пользователь не найден")));
    }

    public Flux<Users> findAllActive() {
        return usersRepository.findAllByIsActiveTrueAndDeletedAtIsNull();
    }
}
