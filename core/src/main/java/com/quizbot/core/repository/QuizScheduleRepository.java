package com.quizbot.core.repository;

import com.quizbot.core.domain.QuizSchedule;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizScheduleRepository extends ReactiveMongoRepository<QuizSchedule, String> {
}
