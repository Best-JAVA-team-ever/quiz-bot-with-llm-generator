package com.quizbot.core.repository;

import com.quizbot.core.domain.Group;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface GroupRepository extends ReactiveMongoRepository<Group, String> {

    // \group invite, \group exclude, \group delete, \group schedule set/off —
    // поиск группы по ID среди неудалённых
    Mono<Group> findByIdAndDeletedAtIsNull(String id);

    // \group list — список всех активных групп
    Flux<Group> findAllByDeletedAtIsNull();

    // Поиск группы по invite-ссылке — при принятии приглашения пользователем
    Mono<Group> findByInviteLinkAndDeletedAtIsNull(String inviteLink);
}
