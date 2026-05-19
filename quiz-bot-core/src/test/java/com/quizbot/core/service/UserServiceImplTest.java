package com.quizbot.core.service;

import com.quizbot.core.domain.User;
import com.quizbot.core.domain.UserRole;
import com.quizbot.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, "100,200");
    }

    @Test
    void getOrCreateUser_shouldReturnExistingUser() {
        User existing = new User(1L, UserRole.USER, LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Mono.just(existing));

        StepVerifier.create(userService.getOrCreateUser(1L))
                .expectNext(existing)
                .verifyComplete();
    }

    @Test
    void getOrCreateUser_shouldCreateNewUser_withUserRole_whenNotInAdminList() {
        User saved = new User(1L, UserRole.USER, LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Mono.empty());
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(userService.getOrCreateUser(1L))
                .expectNextMatches(u -> u.role() == UserRole.USER)
                .verifyComplete();
    }

    @Test
    void getOrCreateUser_shouldCreateNewUser_withAdminRole_whenInAdminList() {
        User saved = new User(100L, UserRole.ADMIN, LocalDateTime.now());
        when(userRepository.findById(100L)).thenReturn(Mono.empty());
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(userService.getOrCreateUser(100L))
                .expectNextMatches(u -> u.role() == UserRole.ADMIN)
                .verifyComplete();
    }

    @Test
    void upgradeUser_shouldSetAdminRole() {
        User user = new User(1L, UserRole.USER, LocalDateTime.now());
        User upgraded = new User(1L, UserRole.ADMIN, user.registrationDate());
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(upgraded));

        StepVerifier.create(userService.upgradeUser(1L))
                .verifyComplete();
    }

    @Test
    void getAllUsers_shouldReturnAllUsers() {
        User u1 = new User(1L, UserRole.USER, LocalDateTime.now());
        User u2 = new User(2L, UserRole.ADMIN, LocalDateTime.now());
        when(userRepository.findAll()).thenReturn(Flux.just(u1, u2));

        StepVerifier.create(userService.getAllUsers())
                .expectNext(u1, u2)
                .verifyComplete();
    }

    @Test
    void getRole_shouldReturnCorrectRole() {
        User user = new User(1L, UserRole.USER, LocalDateTime.now());
        when(userRepository.findById(1L)).thenReturn(Mono.just(user));

        StepVerifier.create(userService.getRole(1L))
                .expectNext(UserRole.USER)
                .verifyComplete();
    }
}
