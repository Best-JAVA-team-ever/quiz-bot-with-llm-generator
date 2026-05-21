package com.quizbot.core.repository;

import com.quizbot.core.domain.GroupMember;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface GroupMemberRepository extends ReactiveMongoRepository<GroupMember, String> {

    // \group list, \group score (Admin) — участники конкретной группы
    Flux<GroupMember> findAllByGroupIdAndDeletedAtIsNull(String groupId);

    // \group leave (User) — список групп, в которых состоит пользователь
    // \group score (User) — группы пользователя для отображения статистики
    Flux<GroupMember> findAllByUserIdAndDeletedAtIsNull(String userId);

    // Проверка членства перед \group invite — чтобы не добавить дважды
    Mono<GroupMember> findByGroupIdAndUserIdAndDeletedAtIsNull(String groupId, String userId);

    // \group exclude, \group leave — мягкое удаление конкретной записи
    Mono<GroupMember> findByGroupIdAndUserId(String groupId, String userId);

    // \group delete — удаление всех участников группы при её удалении
    Flux<GroupMember> findAllByGroupId(String groupId);
}
