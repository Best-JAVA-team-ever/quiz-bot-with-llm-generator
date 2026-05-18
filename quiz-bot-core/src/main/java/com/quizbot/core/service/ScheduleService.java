package com.quizbot.core.service;

import com.quizbot.core.domain.QuizSchedule;
import reactor.core.publisher.Mono;

public interface ScheduleService {
    Mono<QuizSchedule> setGlobalSchedule(String cron, String topic);
    Mono<Void> disableGlobalSchedule();
    Mono<QuizSchedule> getGlobalSchedule();
    
    Mono<QuizSchedule> setGroupSchedule(String groupId, String cron);
    Mono<Void> disableGroupSchedule(String groupId);
    Mono<QuizSchedule> getGroupSchedule(String groupId);
}
