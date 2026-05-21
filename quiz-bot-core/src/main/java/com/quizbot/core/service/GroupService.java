package com.quizbot.core.service;

import com.quizbot.core.domain.Group;
import com.quizbot.core.domain.GroupMember;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GroupService {
    Mono<Group> createGroup(String name);
    Mono<Void> deleteGroup(String groupId);
    Mono<Group> getGroup(String groupId);
    Flux<Group> getAllGroups();
    Flux<Group> getGroupsForUser(Long userId);
    Mono<Void> addUserToGroup(String groupId, Long userId);
    Mono<Void> removeUserFromGroup(String groupId, Long userId);
    Flux<GroupMember> findMembers(String groupId);
}
