package com.quizbot.core.service;

import com.quizbot.core.domain.Group;
import com.quizbot.core.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupRepository groupRepository;

    private GroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupServiceImpl(groupRepository);
    }

    @Test
    void createGroup_shouldSaveGroup_withCorrectInviteLinkFormat() {
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(groupService.createGroup("TestGroup"))
                .expectNextMatches(g ->
                        g.name().equals("TestGroup") &&
                        g.inviteLink().startsWith("https://t.me/quiz_bot?start=join_") &&
                        g.memberIds().isEmpty()
                )
                .verifyComplete();
    }

    @Test
    void createGroup_shouldGenerateIdOfLength8() {
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(groupService.createGroup("TestGroup"))
                .expectNextMatches(g -> g.id().length() == 8)
                .verifyComplete();
    }

    @Test
    void addUserToGroup_shouldAddUserToMemberIds() {
        Group group = new Group("g1", "G1", "link", new HashSet<>());
        when(groupRepository.findById("g1")).thenReturn(Mono.just(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(groupService.addUserToGroup("g1", 42L))
                .verifyComplete();

        assertTrue(group.memberIds().contains(42L));
    }

    @Test
    void removeUserFromGroup_shouldRemoveUserFromMemberIds() {
        Set<Long> members = new HashSet<>(Set.of(42L));
        Group group = new Group("g1", "G1", "link", members);
        when(groupRepository.findById("g1")).thenReturn(Mono.just(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(groupService.removeUserFromGroup("g1", 42L))
                .verifyComplete();

        assertTrue(group.memberIds().isEmpty());
    }

    @Test
    void getGroupsForUser_shouldReturnOnlyGroupsContainingUser() {
        Group g1 = new Group("g1", "G1", "link1", new HashSet<>(Set.of(1L, 2L)));
        Group g2 = new Group("g2", "G2", "link2", new HashSet<>(Set.of(3L)));
        when(groupRepository.findAll()).thenReturn(Flux.just(g1, g2));

        StepVerifier.create(groupService.getGroupsForUser(1L))
                .expectNextMatches(g -> g.id().equals("g1"))
                .verifyComplete();
    }

    @Test
    void generateInviteLink_shouldReturnCorrectFormat() {
        String link = groupService.generateInviteLink("abc123");
        assertEquals("https://t.me/quiz_bot?start=join_abc123", link);
    }
}
