package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Set;

@Document(collection = "groups")
public record Group(
    @Id String id,
    String name,
    String inviteLink,
    Set<Long> memberIds
) {}
