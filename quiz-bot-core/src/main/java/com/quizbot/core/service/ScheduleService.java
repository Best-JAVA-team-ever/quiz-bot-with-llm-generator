package com.quizbot.core.service;

import com.quizbot.core.domain.QuizSchedule;
import com.quizbot.core.repository.QuizScheduleRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ScheduleService {

    private final QuizScheduleRepository scheduleRepository;
    private static final String GLOBAL_ID = "global";

    public ScheduleService(QuizScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    public Mono<QuizSchedule> setGlobalSchedule(String cron, String topic) {
        QuizSchedule schedule = new QuizSchedule(GLOBAL_ID, cron, true, topic);
        return scheduleRepository.save(schedule);
    }

    public Mono<Void> disableGlobalSchedule() {
        return scheduleRepository.findById(GLOBAL_ID)
                .flatMap(s -> scheduleRepository.save(new QuizSchedule(GLOBAL_ID, s.cronExpression(), false, s.topicName())))
                .then();
    }

    public Mono<QuizSchedule> getGlobalSchedule() {
        return scheduleRepository.findById(GLOBAL_ID);
    }

    public Mono<QuizSchedule> setGroupSchedule(String groupId, String cron) {
        String id = "group:" + groupId;
        QuizSchedule schedule = new QuizSchedule(id, cron, true, null);
        return scheduleRepository.save(schedule);
    }

    public Mono<Void> disableGroupSchedule(String groupId) {
        String id = "group:" + groupId;
        return scheduleRepository.findById(id)
                .flatMap(s -> scheduleRepository.save(new QuizSchedule(id, s.cronExpression(), false, null)))
                .then();
    }

    public Mono<QuizSchedule> getGroupSchedule(String groupId) {
        return scheduleRepository.findById("group:" + groupId);
    }
}
