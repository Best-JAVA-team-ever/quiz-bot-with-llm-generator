package com.quizbot.core.service;

import com.quizbot.core.domain.Group;
import com.quizbot.core.domain.GroupMember;
import com.quizbot.core.repository.GroupMemberRepository;
import com.quizbot.core.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupRepository groupRepository;
    
    @Mock
    private GroupMemberRepository groupMemberRepository;
    
    @Mock
    private AuditLogService auditLogService;

    private GroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupServiceImpl(groupRepository, groupMemberRepository, auditLogService);
        Mockito.lenient().when(auditLogService.log(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void createGroup_shouldSaveGroup_withCorrectInviteLinkFormat() {
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(groupService.createGroup("TestGroup"))
                .expectNextMatches(g ->
                        g.name().equals("TestGroup") &&
                        g.inviteLink() != null && g.inviteLink().startsWith("https://t.me/quiz_bot?start=join_")
                )
                .verifyComplete();
    }

    @Test
    void createGroup_shouldGenerateIdOfLength8() {
        // Mock save to return a Group that has the assigned invite link (which includes the id)
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(groupService.createGroup("TestGroup"))
                .expectNextMatches(g -> {
                    // ID is null because we mock save to return the same object, but inviteLink is generated with ID
                    String link = g.inviteLink();
                    String id = link.substring(link.lastIndexOf("_") + 1);
                    return id.length() == 8;
                })
                .verifyComplete();
    }

    @Test
    void addUserToGroup_shouldAddUserToMemberIds() {
        Group group = new Group("g1", "G1", "link", null, Instant.now(), null);
        GroupMember savedMember = new GroupMember("m1", "g1", "42", Instant.now(), null);
        
        when(groupRepository.findByIdAndDeletedAtIsNull("g1")).thenReturn(Mono.just(group));
        when(groupMemberRepository.findByGroupIdAndUserIdAndDeletedAtIsNull("g1", "42")).thenReturn(Mono.empty());
        when(groupMemberRepository.save(any(GroupMember.class))).thenReturn(Mono.just(savedMember));

        StepVerifier.create(groupService.addUserToGroup("g1", 42L))
                .verifyComplete();
    }

    @Test
    void removeUserFromGroup_shouldRemoveUserFromMemberIds() {
        GroupMember member = new GroupMember("m1", "g1", "42", Instant.now(), null);
        
        when(groupMemberRepository.findByGroupIdAndUserIdAndDeletedAtIsNull("g1", "42")).thenReturn(Mono.just(member));
        when(groupMemberRepository.save(any(GroupMember.class))).thenReturn(Mono.just(member.markAsDeleted()));

        StepVerifier.create(groupService.removeUserFromGroup("g1", 42L))
                .verifyComplete();
    }

    @Test
    void getGroupsForUser_shouldReturnOnlyGroupsContainingUser() {
        Group g1 = new Group("g1", "G1", "link1", null, Instant.now(), null);
        GroupMember member1 = new GroupMember("m1", "g1", "1", Instant.now(), null);
        
        when(groupMemberRepository.findAllByUserIdAndDeletedAtIsNull("1")).thenReturn(Flux.just(member1));
        when(groupRepository.findByIdAndDeletedAtIsNull("g1")).thenReturn(Mono.just(g1));

        StepVerifier.create(groupService.getGroupsForUser(1L))
                .expectNextMatches(g -> g.name().equals("G1"))
                .verifyComplete();
    }

    @Test
    void generateInviteLink_shouldReturnCorrectFormat() {
        String link = groupService.generateInviteLink("abc123");
        assertEquals("https://t.me/quiz_bot?start=join_abc123", link);
    }
}