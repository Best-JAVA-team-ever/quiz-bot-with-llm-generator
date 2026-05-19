package com.quizbot.core.service;

import com.quizbot.core.domain.Role;
import com.quizbot.core.domain.Users;
import com.quizbot.core.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UsersRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, auditLogService, "100,200");
        Mockito.lenient().when(auditLogService.log(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void getOrCreateUser_shouldReturnExistingUser() {
        Users existing = Users.create(1L, Role.USER);
        when(userRepository.findByTelegramIdAndDeletedAtIsNull(1L)).thenReturn(Mono.just(existing));

        StepVerifier.create(userService.getOrCreateUser(1L))
                .expectNext(existing)
                .verifyComplete();
    }

    @Test
    void getOrCreateUser_shouldCreateNewUser_withUserRole_whenNotInAdminList() {
        Users saved = Users.create(1L, Role.USER);
        when(userRepository.findByTelegramIdAndDeletedAtIsNull(1L)).thenReturn(Mono.empty());
        when(userRepository.save(any(Users.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(userService.getOrCreateUser(1L))
                .expectNextMatches(u -> u.role() == Role.USER)
                .verifyComplete();
    }

    @Test
    void getOrCreateUser_shouldCreateNewUser_withAdminRole_whenInAdminList() {
        Users saved = Users.create(100L, Role.ADMIN);
        when(userRepository.findByTelegramIdAndDeletedAtIsNull(100L)).thenReturn(Mono.empty());
        when(userRepository.save(any(Users.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(userService.getOrCreateUser(100L))
                .expectNextMatches(u -> u.role() == Role.ADMIN)
                .verifyComplete();
    }

    @Test
    void upgradeUser_shouldSetAdminRole() {
        Users user = Users.create(1L, Role.USER);
        Users upgraded = user.withRole(Role.ADMIN);
        when(userRepository.findByTelegramId(1L)).thenReturn(Mono.just(user));
        when(userRepository.save(any(Users.class))).thenReturn(Mono.just(upgraded));

        StepVerifier.create(userService.upgradeUser(1L))
                .verifyComplete();
    }

    @Test
    void getAllUsers_shouldReturnAllUsers() {
        Users u1 = Users.create(1L, Role.USER);
        Users u2 = Users.create(2L, Role.ADMIN);
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(Flux.just(u1, u2));

        StepVerifier.create(userService.getAllUsers())
                .expectNext(u1, u2)
                .verifyComplete();
    }

    @Test
    void getRole_shouldReturnCorrectRole() {
        Users user = Users.create(1L, Role.USER);
        when(userRepository.findByTelegramIdAndDeletedAtIsNull(1L)).thenReturn(Mono.just(user));

        StepVerifier.create(userService.getRole(1L))
                .expectNext(Role.USER)
                .verifyComplete();
    }
}