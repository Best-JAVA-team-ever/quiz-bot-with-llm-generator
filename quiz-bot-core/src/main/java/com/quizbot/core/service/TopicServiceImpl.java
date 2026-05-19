package com.quizbot.core.service;

import com.quizbot.core.domain.CollectionsName;
import com.quizbot.core.domain.Topic;
import com.quizbot.core.repository.QuestionRepository;
import com.quizbot.core.repository.TopicRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

@Service
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final AuditLogService auditLogService;

    public TopicServiceImpl(TopicRepository topicRepository,
            QuestionRepository questionRepository,
            AuditLogService auditLogService) {
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public Mono<Topic> addTopic(String name) {
        return create(null, name);
    }

    public Mono<Topic> create(String initiatorId, String name) {
        if (!isValid(name)) {
            return Mono.error(new IllegalArgumentException("Тема " + name + " некорректна"));
        }
        return topicRepository.findByNameAndDeletedAtIsNull(name)
                .flatMap(existing -> Mono.<Topic>error(
                        new IllegalStateException("Тема " + name + " уже существует")))
                .switchIfEmpty(Mono.defer(() -> {
                    Topic topic = Topic.create(name);
                    return topicRepository.save(topic);
                }))
                .flatMap(topic -> {
                    if (initiatorId == null)
                        return Mono.just(topic);
                    return auditLogService
                            .log(initiatorId, "CREATE_TOPIC",
                                    CollectionsName.TOPIC.name(), topic.id())
                            .thenReturn(topic);
                });
    }

    @Override
    public Flux<Topic> getAllTopics() {
        return topicRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public Mono<Void> deleteTopic(String name) {
        return delete(null, name);
    }

    public Mono<Void> delete(String initiatorId, String name) {
        return topicRepository.findByNameAndDeletedAtIsNull(name)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Данной темы не существует")))
                .flatMap(topic -> questionRepository
                        .existsByTopicNamesContainingAndDeletedAtIsNull(topic.id())
                        .flatMap(hasQuestions -> {
                            if (hasQuestions) {
                                return Mono.<Void>error(new IllegalStateException(
                                        "Нельзя удалить тему, по которой есть вопросы"));
                            }
                            return topicRepository.save(topic.markAsDeleted())
                                    .flatMap(saved -> {
                                        if (initiatorId == null)
                                            return Mono.empty();
                                        return auditLogService.log(initiatorId, "DELETE_TOPIC",
                                                CollectionsName.TOPIC.name(), saved.id());
                                    });
                        }));
    }

    @Override
    public Mono<Boolean> exists(String name) {
        return topicRepository.findByNameAndDeletedAtIsNull(name).hasElement();
    }

    @Override
    public boolean isValid(String name) {
        if (name == null || name.equalsIgnoreCase("all"))
            return false;
        return name.matches("^[а-яА-Яa-zA-Z0-9\\s.,!?;:\\-\"'()]{2,32}$");
    }

    // --- дополнительные методы, используемые внутри модуля ---

    public Mono<Topic> findOrCreate(String initiatorId, String name) {
        return topicRepository.findByNameAndDeletedAtIsNull(name)
                .switchIfEmpty(create(initiatorId, name));
    }

    public Mono<Topic> rename(String initiatorId, String topicId, String newName) {
    if (!isValid(newName)) {
        return Mono.error(new IllegalArgumentException("Тема " + newName + " некорректна"));
    }
    return topicRepository.findByIdAndDeletedAtIsNull(topicId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Тема не найдена")))
            .flatMap(topic -> {
                String oldName = topic.name();
                return topicRepository.save(topic.withName(newName))
                        .flatMap(saved ->
                            questionRepository
                                .findAllByTopicNamesContainingAndDeletedAtIsNull(oldName)
                                .flatMap(q -> {
                                    List<String> updated = q.topicNames().stream()
                                        .map(n -> n.equals(oldName) ? newName : n)
                                        .toList();
                                    return questionRepository.save(q.withTopicNames(updated));
                                })
                                .then(Mono.just(saved))
                        );
            })
            .flatMap(topic -> {
                if (initiatorId == null) return Mono.just(topic);
                return auditLogService
                        .log(initiatorId, "RENAME_TOPIC", CollectionsName.TOPIC.name(), topic.id())
                        .thenReturn(topic);
            });
}

    public Mono<Topic> findByName(String name) {
        return topicRepository.findByNameAndDeletedAtIsNull(name)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Темы " + name + " не существует")));
    }

    public Flux<Topic> findAllByIds(Collection<String> ids) {
        return topicRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }
}
