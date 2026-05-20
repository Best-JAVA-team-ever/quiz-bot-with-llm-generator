package com.quizbot.core.service;

import com.quizbot.core.domain.CollectionsName;
import com.quizbot.core.domain.Group;
import com.quizbot.core.domain.GroupMember;
import com.quizbot.core.repository.GroupMemberRepository;
import com.quizbot.core.repository.GroupRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AuditLogService auditLogService;

    public GroupServiceImpl(GroupRepository groupRepository,
                            GroupMemberRepository groupMemberRepository,
                            AuditLogService auditLogService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public Mono<Group> createGroup(String name) {
        return create(null, name);
    }

    public Mono<Group> create(String initiatorId, String name) {
        String inviteId = UUID.randomUUID().toString().substring(0, 8);
        String inviteLink = "https://t.me/quiz_bot?start=join_" + inviteId;
        Group group = new Group(inviteId, name, inviteLink, null, java.time.Instant.now(), null);
        return groupRepository.save(group)
                .flatMap(saved -> {
                    if (initiatorId == null) return Mono.just(saved);
                    return auditLogService
                            .log(initiatorId, "CREATE_GROUP",
                                    CollectionsName.GROUPS.name(), saved.id())
                            .thenReturn(saved);
                });
    }

    @Override
    public Mono<Void> deleteGroup(String groupId) {
        return delete(null, groupId);
    }

    public Mono<Void> delete(String initiatorId, String groupId) {
        return groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Группа не найдена")))
                .flatMap(group -> groupRepository.save(group.markAsDeleted()))
                .flatMap(group -> groupMemberRepository.findAllByGroupId(groupId)
                        .flatMap(member -> groupMemberRepository.save(member.markAsDeleted()))
                        .then())
                .flatMap(v -> {
                    if (initiatorId == null) return Mono.empty();
                    return auditLogService.log(initiatorId, "DELETE_GROUP",
                            CollectionsName.GROUPS.name(), groupId);
                });
    }

    @Override
    public Mono<Group> getGroup(String groupId) {
        return groupRepository.findByIdAndDeletedAtIsNull(groupId);
    }

    @Override
    public Flux<Group> getAllGroups() {
        return groupRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public Flux<Group> getGroupsForUser(Long userId) {
        return groupMemberRepository.findAllByUserIdAndDeletedAtIsNull(String.valueOf(userId))
                .flatMap(member -> groupRepository.findByIdAndDeletedAtIsNull(member.groupId()));
    }

    @Override
    public Mono<Void> addUserToGroup(String groupId, Long userId) {
        return addMember(null, groupId, String.valueOf(userId)).then();
    }

    public Mono<GroupMember> addMember(String initiatorId, String groupId, String userId) {
        return groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Группа не найдена")))
                .flatMap(group -> groupMemberRepository
                        .findByGroupIdAndUserIdAndDeletedAtIsNull(groupId, userId)
                        .flatMap(existing -> Mono.<GroupMember>error(
                                new IllegalStateException("Пользователь уже в группе")))
                        .switchIfEmpty(Mono.defer(() ->
                                groupMemberRepository.save(GroupMember.create(groupId, userId)))))
                .flatMap(member -> {
                    if (initiatorId == null) return Mono.just(member);
                    return auditLogService
                            .log(initiatorId, "ADD_GROUP_MEMBER",
                                    CollectionsName.GROUP_MEMBERS.name(), member.id())
                            .thenReturn(member);
                });
    }

    @Override
    public Mono<Void> removeUserFromGroup(String groupId, Long userId) {
        return removeMember(null, groupId, String.valueOf(userId));
    }

    public Mono<Void> removeMember(String initiatorId, String groupId, String userId) {
        return groupMemberRepository.findByGroupIdAndUserIdAndDeletedAtIsNull(groupId, userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Пользователь не состоит в группе")))
                .flatMap(member -> groupMemberRepository.save(member.markAsDeleted()))
                .flatMap(member -> {
                    if (initiatorId == null) return Mono.empty();
                    return auditLogService.log(initiatorId, "REMOVE_GROUP_MEMBER",
                            CollectionsName.GROUP_MEMBERS.name(), member.id());
                });
    }

    @Override
    public String generateInviteLink(String groupId) {
        return "https://t.me/quiz_bot?start=join_" + groupId;
    }

    public Mono<Void> leave(String userId, String groupId) {
        return removeMember(userId, groupId, userId);
    }

    public Flux<GroupMember> findMembers(String groupId) {
        return groupMemberRepository.findAllByGroupIdAndDeletedAtIsNull(groupId);
    }

    public Mono<Group> findByInviteLink(String inviteLink) {
        return groupRepository.findByInviteLinkAndDeletedAtIsNull(inviteLink)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Приглашение недействительно")));
    }

    public Mono<Group> setSchedule(String groupId, String cron) {
        return groupRepository.findByIdAndDeletedAtIsNull(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Группа не найдена")))
                .flatMap(group -> groupRepository.save(group.withScheduleCron(cron)));
    }

    public Mono<Group> disableSchedule(String groupId) {
        return setSchedule(groupId, null);
    }
}