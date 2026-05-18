package com.quizbot.core.repository;

import com.quizbot.core.domain.Group;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends ReactiveMongoRepository<Group, String> {
}
