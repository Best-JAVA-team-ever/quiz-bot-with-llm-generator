package com.quizbot.core.service;

import com.quizbot.core.domain.QuizSchedule;
import com.quizbot.core.repository.QuizScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @Mock
    private QuizScheduleRepository scheduleRepository;

    private ScheduleServiceImpl scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleServiceImpl(scheduleRepository);
    }

    @Test
    void setGlobalSchedule_shouldSaveSchedule_withGlobalId() {
        QuizSchedule saved = new QuizSchedule("global", "0 8 * * *", true, "Math");
        when(scheduleRepository.save(any(QuizSchedule.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(scheduleService.setGlobalSchedule("0 8 * * *", "Math"))
                .expectNextMatches(s ->
                        s.id().equals("global") &&
                        s.cronExpression().equals("0 8 * * *") &&
                        s.isActive() &&
                        s.topicName().equals("Math")
                )
                .verifyComplete();
    }

    @Test
    void disableGlobalSchedule_shouldSetIsActiveFalse() {
        QuizSchedule existing = new QuizSchedule("global", "0 8 * * *", true, "Math");
        QuizSchedule disabled = new QuizSchedule("global", "0 8 * * *", false, "Math");
        when(scheduleRepository.findById("global")).thenReturn(Mono.just(existing));
        when(scheduleRepository.save(any(QuizSchedule.class))).thenReturn(Mono.just(disabled));

        StepVerifier.create(scheduleService.disableGlobalSchedule())
                .verifyComplete();
    }

    @Test
    void getGlobalSchedule_shouldReturnScheduleFromRepository() {
        QuizSchedule schedule = new QuizSchedule("global", "0 8 * * *", true, null);
        when(scheduleRepository.findById("global")).thenReturn(Mono.just(schedule));

        StepVerifier.create(scheduleService.getGlobalSchedule())
                .expectNext(schedule)
                .verifyComplete();
    }

    @Test
    void setGroupSchedule_shouldSaveSchedule_withGroupPrefixedId() {
        QuizSchedule saved = new QuizSchedule("group:g1", "0 10 * * *", true, null);
        when(scheduleRepository.save(any(QuizSchedule.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(scheduleService.setGroupSchedule("g1", "0 10 * * *"))
                .expectNextMatches(s -> s.id().equals("group:g1") && s.isActive())
                .verifyComplete();
    }

    @Test
    void disableGroupSchedule_shouldSetIsActiveFalse() {
        QuizSchedule existing = new QuizSchedule("group:g1", "0 10 * * *", true, null);
        QuizSchedule disabled = new QuizSchedule("group:g1", "0 10 * * *", false, null);
        when(scheduleRepository.findById("group:g1")).thenReturn(Mono.just(existing));
        when(scheduleRepository.save(any(QuizSchedule.class))).thenReturn(Mono.just(disabled));

        StepVerifier.create(scheduleService.disableGroupSchedule("g1"))
                .verifyComplete();
    }

    @Test
    void getGroupSchedule_shouldReturnScheduleFromRepository() {
        QuizSchedule schedule = new QuizSchedule("group:g1", "0 10 * * *", true, null);
        when(scheduleRepository.findById("group:g1")).thenReturn(Mono.just(schedule));

        StepVerifier.create(scheduleService.getGroupSchedule("g1"))
                .expectNext(schedule)
                .verifyComplete();
    }
}
