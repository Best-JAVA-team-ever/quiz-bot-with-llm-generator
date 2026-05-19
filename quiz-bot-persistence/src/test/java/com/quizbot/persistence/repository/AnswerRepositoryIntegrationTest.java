// package com.quizbot.persistence.repository;

// import com.quizbot.core.domain.Answers;
// import com.quizbot.core.repository.AnswersRepository;
// import com.quizbot.persistence.MongoConfig;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Disabled;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
// import org.springframework.test.context.ContextConfiguration;
// import org.springframework.test.context.junit.jupiter.SpringExtension;
// import org.testcontainers.containers.MongoDBContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;
// import reactor.test.StepVerifier;

// import java.time.LocalDateTime;
// import java.util.List;

// @Testcontainers
// @ExtendWith(SpringExtension.class)
// @ContextConfiguration(classes = {MongoConfig.class, AnswerRepositoryIntegrationTest.TestConfig.class})
// @Disabled("Cannot run Testcontainers reliably in this nested Docker-in-Docker CLI environment")
// class AnswerRepositoryIntegrationTest {

//     @Container
//     static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:latest");

//     @Configuration
//     static class TestConfig {
//         @Bean
//         public static PropertySourcesPlaceholderConfigurer properties() {
//             PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
//             java.util.Properties properties = new java.util.Properties();
//             properties.setProperty("spring.data.mongodb.uri", mongoDBContainer.getReplicaSetUrl());
//             configurer.setProperties(properties);
//             return configurer;
//         }
//     }

//     @Autowired
//     private AnswersRepository answersRepository;

//     @BeforeEach
//     void setUp() {
//         answersRepository.deleteAll().block();
//     }

//     @Test
//     void shouldSaveAndFindAnswersByUserId() {
//         Long userId = 123L;
//         Answers answer1 = new Answers(null, "q1", userId, "A", true, LocalDateTime.now());
//         Answers answer2 = new Answers(null, "q2", userId, "B", false, LocalDateTime.now());
//         Answers answer3 = new Answers(null, "q1", 999L, "C", true, LocalDateTime.now());

//         answersRepository.saveAll(List.of(answer1, answer2, answer3)).blockLast();

//         StepVerifier.create(answersRepository.findByUserId(userId))
//                 .expectNextCount(2)
//                 .verifyComplete();
//     }

//     @Test
//     void shouldFindOnlyCorrectAnswers() {
//         Long userId = 123L;
//         Answers answer1 = new Answers(null, "q1", userId, "A", true, LocalDateTime.now());
//         Answers answer2 = new Answers(null, "q2", userId, "B", false, LocalDateTime.now());

//         answersRepository.saveAll(List.of(answer1, answer2)).blockLast();

//         StepVerifier.create(answersRepository.findByUserIdAndIsCorrectTrue(userId))
//                 .expectNextMatches(a -> a.questionId().equals("q1"))
//                 .verifyComplete();
//     }

//     @Test
//     void shouldDeleteAnswersByUserId() {
//         Long userId = 123L;
//         Answers answer1 = Answers.create(userId,"q1", "A", true);
//         answersRepository.save(answer1).block();

//         StepVerifier.create(answersRepository.deleteByUserId(userId))
//                 .verifyComplete();

//         StepVerifier.create(answersRepository.findByUserId(userId))
//                 .verifyComplete(); // Expecting 0 elements
//     }
// }
