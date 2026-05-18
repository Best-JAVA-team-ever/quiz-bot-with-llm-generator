package com.quizbot.core.service;

import com.quizbot.core.domain.Group;
import com.quizbot.core.repository.GroupRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.UUID;

@Service
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;

    public GroupServiceImpl(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Override
    public Mono<Group> createGroup(String name) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String inviteLink = "https://t.me/quiz_bot?start=join_" + id;
        Group group = new Group(id, name, inviteLink, new HashSet<>());
        return groupRepository.save(group);
    }

    @Override
    public Mono<Void> deleteGroup(String groupId) {
        return groupRepository.deleteById(groupId);
    }

    @Override
    public Mono<Group> getGroup(String groupId) {
        return groupRepository.findById(groupId);
    }

    @Override
    public Flux<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    @Override
    public Flux<Group> getGroupsForUser(Long userId) {
        return groupRepository.findAll()
                .filter(g -> g.memberIds().contains(userId));
    }

    @Override
    public Mono<Void> addUserToGroup(String groupId, Long userId) {
        return groupRepository.findById(groupId)
                .flatMap(g -> {
                    g.memberIds().add(userId);
                    return groupRepository.save(g);
                })
                .then();
    }

    @Override
    public Mono<Void> removeUserFromGroup(String groupId, Long userId) {
        return groupRepository.findById(groupId)
                .flatMap(g -> {
                    g.memberIds().remove(userId);
                    return groupRepository.save(g);
                })
                .then();
    }

    @Override
    public String generateInviteLink(String groupId) {
        return "https://t.me/quiz_bot?start=join_" + groupId;
    }
}
