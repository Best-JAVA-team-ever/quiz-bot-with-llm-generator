package com.quizbot.core.service;

import com.quizbot.core.domain.*;
import com.quizbot.core.repository.*;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Publisher;

import java.time.Instant;
import java.util.Collection;
import java.util.function.Function;

/**
 * Минимальные stub-реализации репозиториев для unit-тестов без Mockito.
 * Каждый абстрактный класс реализует все методы интерфейса с пустыми дефолтами.
 * В тестах создаётся анонимный подкласс, переопределяющий только нужные методы.
 */
class TestStubs {

    // ─────────────────────────────────────────────
    // QuestionRepository
    // ─────────────────────────────────────────────
    abstract static class QuestionRepo implements QuestionRepository {
        @Override public <S extends Question> Mono<S> save(S e) { return Mono.just(e); }
        @Override public <S extends Question> Flux<S> saveAll(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends Question> Flux<S> saveAll(Publisher<S> e) { return Flux.from(e); }
        @Override public Mono<Question> findById(String id) { return Mono.empty(); }
        @Override public Mono<Question> findById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Boolean> existsById(String id) { return Mono.just(false); }
        @Override public Mono<Boolean> existsById(Publisher<String> id) { return Mono.just(false); }
        @Override public Flux<Question> findAll() { return Flux.empty(); }
        @Override public Flux<Question> findAll(Sort sort) { return Flux.empty(); }
        @Override public Flux<Question> findAllById(Iterable<String> ids) { return Flux.empty(); }
        @Override public Flux<Question> findAllById(Publisher<String> ids) { return Flux.empty(); }
        @Override public Mono<Long> count() { return Mono.just(0L); }
        @Override public Mono<Void> deleteById(String id) { return Mono.empty(); }
        @Override public Mono<Void> deleteById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Void> delete(Question e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAllById(Iterable<? extends String> ids) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Iterable<? extends Question> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Publisher<? extends Question> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll() { return Mono.empty(); }
        @Override public <S extends Question> Mono<S> insert(S e) { return Mono.just(e); }
        @Override public <S extends Question> Flux<S> insert(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends Question> Flux<S> insert(Publisher<S> e) { return Flux.from(e); }
        @Override public <S extends Question> Mono<S> findOne(Example<S> e) { return Mono.empty(); }
        @Override public <S extends Question> Flux<S> findAll(Example<S> e) { return Flux.empty(); }
        @Override public <S extends Question> Flux<S> findAll(Example<S> e, Sort s) { return Flux.empty(); }
        @Override public <S extends Question> Mono<Long> count(Example<S> e) { return Mono.just(0L); }
        @Override public <S extends Question> Mono<Boolean> exists(Example<S> e) { return Mono.just(false); }
        @Override public <S extends Question, R> Publisher<R> findBy(Example<S> e, Function<FluentQuery.ReactiveFluentQuery<S>, Publisher<R>> q) { return Mono.empty(); }
        @Override public Mono<Question> findByIdAndDeletedAtIsNull(String id) { return Mono.empty(); }
        @Override public Flux<Question> findAllByDeletedAtIsNull() { return Flux.empty(); }
        @Override public Flux<Question> findAllByTopicNamesContainingAndDeletedAtIsNull(String t) { return Flux.empty(); }
        @Override public Flux<Question> findAllByTopicNamesInAndDeletedAtIsNull(Collection<String> t) { return Flux.empty(); }
        @Override public Mono<Long> countByTopicNamesContainingAndDeletedAtIsNull(String t) { return Mono.just(0L); }
        @Override public Mono<Boolean> existsByTopicNamesContainingAndDeletedAtIsNull(String t) { return Mono.just(false); }
    }

    // ─────────────────────────────────────────────
    // AnswersRepository
    // ─────────────────────────────────────────────
    abstract static class AnswersRepo implements AnswersRepository {
        @Override public <S extends Answers> Mono<S> save(S e) { return Mono.just(e); }
        @Override public <S extends Answers> Flux<S> saveAll(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends Answers> Flux<S> saveAll(Publisher<S> e) { return Flux.from(e); }
        @Override public Mono<Answers> findById(String id) { return Mono.empty(); }
        @Override public Mono<Answers> findById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Boolean> existsById(String id) { return Mono.just(false); }
        @Override public Mono<Boolean> existsById(Publisher<String> id) { return Mono.just(false); }
        @Override public Flux<Answers> findAll() { return Flux.empty(); }
        @Override public Flux<Answers> findAll(Sort sort) { return Flux.empty(); }
        @Override public Flux<Answers> findAllById(Iterable<String> ids) { return Flux.empty(); }
        @Override public Flux<Answers> findAllById(Publisher<String> ids) { return Flux.empty(); }
        @Override public Mono<Long> count() { return Mono.just(0L); }
        @Override public Mono<Void> deleteById(String id) { return Mono.empty(); }
        @Override public Mono<Void> deleteById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Void> delete(Answers e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAllById(Iterable<? extends String> ids) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Iterable<? extends Answers> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Publisher<? extends Answers> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll() { return Mono.empty(); }
        @Override public <S extends Answers> Mono<S> insert(S e) { return Mono.just(e); }
        @Override public <S extends Answers> Flux<S> insert(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends Answers> Flux<S> insert(Publisher<S> e) { return Flux.from(e); }
        @Override public <S extends Answers> Mono<S> findOne(Example<S> e) { return Mono.empty(); }
        @Override public <S extends Answers> Flux<S> findAll(Example<S> e) { return Flux.empty(); }
        @Override public <S extends Answers> Flux<S> findAll(Example<S> e, Sort s) { return Flux.empty(); }
        @Override public <S extends Answers> Mono<Long> count(Example<S> e) { return Mono.just(0L); }
        @Override public <S extends Answers> Mono<Boolean> exists(Example<S> e) { return Mono.just(false); }
        @Override public <S extends Answers, R> Publisher<R> findBy(Example<S> e, Function<FluentQuery.ReactiveFluentQuery<S>, Publisher<R>> q) { return Mono.empty(); }
        @Override public Flux<Answers> findAllByUserIdAndAnsweredAtAfter(String userId, Instant after) { return Flux.empty(); }
        @Override public Flux<Answers> findAllByUserIdAndQuestionIdInAndAnsweredAtAfter(String userId, Collection<String> questionIds, Instant after) { return Flux.empty(); }
        @Override public Flux<Answers> findAllByUserIdIn(Collection<String> userIds) { return Flux.empty(); }
        @Override public Flux<Answers> findAllByUserIdInAndQuestionIdIn(Collection<String> userIds, Collection<String> questionIds) { return Flux.empty(); }
        @Override public Flux<Answers> findAllByQuestionIdIn(Collection<String> questionIds) { return Flux.empty(); }
    }

    // ─────────────────────────────────────────────
    // UsersRepository
    // ─────────────────────────────────────────────
    abstract static class UsersRepo implements UsersRepository {
        @Override public <S extends Users> Mono<S> save(S e) { return Mono.just(e); }
        @Override public <S extends Users> Flux<S> saveAll(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends Users> Flux<S> saveAll(Publisher<S> e) { return Flux.from(e); }
        @Override public Mono<Users> findById(String id) { return Mono.empty(); }
        @Override public Mono<Users> findById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Boolean> existsById(String id) { return Mono.just(false); }
        @Override public Mono<Boolean> existsById(Publisher<String> id) { return Mono.just(false); }
        @Override public Flux<Users> findAll() { return Flux.empty(); }
        @Override public Flux<Users> findAll(Sort sort) { return Flux.empty(); }
        @Override public Flux<Users> findAllById(Iterable<String> ids) { return Flux.empty(); }
        @Override public Flux<Users> findAllById(Publisher<String> ids) { return Flux.empty(); }
        @Override public Mono<Long> count() { return Mono.just(0L); }
        @Override public Mono<Void> deleteById(String id) { return Mono.empty(); }
        @Override public Mono<Void> deleteById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Void> delete(Users e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAllById(Iterable<? extends String> ids) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Iterable<? extends Users> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Publisher<? extends Users> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll() { return Mono.empty(); }
        @Override public <S extends Users> Mono<S> insert(S e) { return Mono.just(e); }
        @Override public <S extends Users> Flux<S> insert(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends Users> Flux<S> insert(Publisher<S> e) { return Flux.from(e); }
        @Override public <S extends Users> Mono<S> findOne(Example<S> e) { return Mono.empty(); }
        @Override public <S extends Users> Flux<S> findAll(Example<S> e) { return Flux.empty(); }
        @Override public <S extends Users> Flux<S> findAll(Example<S> e, Sort s) { return Flux.empty(); }
        @Override public <S extends Users> Mono<Long> count(Example<S> e) { return Mono.just(0L); }
        @Override public <S extends Users> Mono<Boolean> exists(Example<S> e) { return Mono.just(false); }
        @Override public <S extends Users, R> Publisher<R> findBy(Example<S> e, Function<FluentQuery.ReactiveFluentQuery<S>, Publisher<R>> q) { return Mono.empty(); }
        @Override public Mono<Users> findByTelegramId(Long telegramId) { return Mono.empty(); }
        @Override public Mono<Users> findByTelegramIdAndDeletedAtIsNull(Long telegramId) { return Mono.empty(); }
        @Override public Flux<Users> findAllByDeletedAtIsNull() { return Flux.empty(); }
        @Override public Flux<Users> findAllByIsActiveTrueAndDeletedAtIsNull() { return Flux.empty(); }
        @Override public Mono<Users> findByIdAndDeletedAtIsNull(String id) { return Mono.empty(); }
    }

    // ─────────────────────────────────────────────
    // GroupRepository
    // ─────────────────────────────────────────────
    abstract static class GroupRepo implements GroupRepository {
        @Override public <S extends Group> Mono<S> save(S e) { return Mono.just(e); }
        @Override public <S extends Group> Flux<S> saveAll(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends Group> Flux<S> saveAll(Publisher<S> e) { return Flux.from(e); }
        @Override public Mono<Group> findById(String id) { return Mono.empty(); }
        @Override public Mono<Group> findById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Boolean> existsById(String id) { return Mono.just(false); }
        @Override public Mono<Boolean> existsById(Publisher<String> id) { return Mono.just(false); }
        @Override public Flux<Group> findAll() { return Flux.empty(); }
        @Override public Flux<Group> findAll(Sort sort) { return Flux.empty(); }
        @Override public Flux<Group> findAllById(Iterable<String> ids) { return Flux.empty(); }
        @Override public Flux<Group> findAllById(Publisher<String> ids) { return Flux.empty(); }
        @Override public Mono<Long> count() { return Mono.just(0L); }
        @Override public Mono<Void> deleteById(String id) { return Mono.empty(); }
        @Override public Mono<Void> deleteById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Void> delete(Group e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAllById(Iterable<? extends String> ids) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Iterable<? extends Group> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Publisher<? extends Group> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll() { return Mono.empty(); }
        @Override public <S extends Group> Mono<S> insert(S e) { return Mono.just(e); }
        @Override public <S extends Group> Flux<S> insert(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends Group> Flux<S> insert(Publisher<S> e) { return Flux.from(e); }
        @Override public <S extends Group> Mono<S> findOne(Example<S> e) { return Mono.empty(); }
        @Override public <S extends Group> Flux<S> findAll(Example<S> e) { return Flux.empty(); }
        @Override public <S extends Group> Flux<S> findAll(Example<S> e, Sort s) { return Flux.empty(); }
        @Override public <S extends Group> Mono<Long> count(Example<S> e) { return Mono.just(0L); }
        @Override public <S extends Group> Mono<Boolean> exists(Example<S> e) { return Mono.just(false); }
        @Override public <S extends Group, R> Publisher<R> findBy(Example<S> e, Function<FluentQuery.ReactiveFluentQuery<S>, Publisher<R>> q) { return Mono.empty(); }
        @Override public Mono<Group> findByIdAndDeletedAtIsNull(String id) { return Mono.empty(); }
        @Override public Flux<Group> findAllByDeletedAtIsNull() { return Flux.empty(); }
        @Override public Mono<Group> findByInviteLinkAndDeletedAtIsNull(String link) { return Mono.empty(); }
    }

    // ─────────────────────────────────────────────
    // GroupMemberRepository
    // ─────────────────────────────────────────────
    abstract static class GroupMemberRepo implements GroupMemberRepository {
        @Override public <S extends GroupMember> Mono<S> save(S e) { return Mono.just(e); }
        @Override public <S extends GroupMember> Flux<S> saveAll(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends GroupMember> Flux<S> saveAll(Publisher<S> e) { return Flux.from(e); }
        @Override public Mono<GroupMember> findById(String id) { return Mono.empty(); }
        @Override public Mono<GroupMember> findById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Boolean> existsById(String id) { return Mono.just(false); }
        @Override public Mono<Boolean> existsById(Publisher<String> id) { return Mono.just(false); }
        @Override public Flux<GroupMember> findAll() { return Flux.empty(); }
        @Override public Flux<GroupMember> findAll(Sort sort) { return Flux.empty(); }
        @Override public Flux<GroupMember> findAllById(Iterable<String> ids) { return Flux.empty(); }
        @Override public Flux<GroupMember> findAllById(Publisher<String> ids) { return Flux.empty(); }
        @Override public Mono<Long> count() { return Mono.just(0L); }
        @Override public Mono<Void> deleteById(String id) { return Mono.empty(); }
        @Override public Mono<Void> deleteById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Void> delete(GroupMember e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAllById(Iterable<? extends String> ids) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Iterable<? extends GroupMember> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Publisher<? extends GroupMember> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll() { return Mono.empty(); }
        @Override public <S extends GroupMember> Mono<S> insert(S e) { return Mono.just(e); }
        @Override public <S extends GroupMember> Flux<S> insert(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends GroupMember> Flux<S> insert(Publisher<S> e) { return Flux.from(e); }
        @Override public <S extends GroupMember> Mono<S> findOne(Example<S> e) { return Mono.empty(); }
        @Override public <S extends GroupMember> Flux<S> findAll(Example<S> e) { return Flux.empty(); }
        @Override public <S extends GroupMember> Flux<S> findAll(Example<S> e, Sort s) { return Flux.empty(); }
        @Override public <S extends GroupMember> Mono<Long> count(Example<S> e) { return Mono.just(0L); }
        @Override public <S extends GroupMember> Mono<Boolean> exists(Example<S> e) { return Mono.just(false); }
        @Override public <S extends GroupMember, R> Publisher<R> findBy(Example<S> e, Function<FluentQuery.ReactiveFluentQuery<S>, Publisher<R>> q) { return Mono.empty(); }
        @Override public Flux<GroupMember> findAllByGroupIdAndDeletedAtIsNull(String groupId) { return Flux.empty(); }
        @Override public Flux<GroupMember> findAllByUserIdAndDeletedAtIsNull(String userId) { return Flux.empty(); }
        @Override public Mono<GroupMember> findByGroupIdAndUserIdAndDeletedAtIsNull(String groupId, String userId) { return Mono.empty(); }
        @Override public Mono<GroupMember> findByGroupIdAndUserId(String groupId, String userId) { return Mono.empty(); }
        @Override public Flux<GroupMember> findAllByGroupId(String groupId) { return Flux.empty(); }
    }

    // ─────────────────────────────────────────────
    // QuizScheduleRepository (нет кастомных методов)
    // ─────────────────────────────────────────────
    abstract static class ScheduleRepo implements QuizScheduleRepository {
        @Override public <S extends QuizSchedule> Mono<S> save(S e) { return Mono.just(e); }
        @Override public <S extends QuizSchedule> Flux<S> saveAll(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends QuizSchedule> Flux<S> saveAll(Publisher<S> e) { return Flux.from(e); }
        @Override public Mono<QuizSchedule> findById(String id) { return Mono.empty(); }
        @Override public Mono<QuizSchedule> findById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Boolean> existsById(String id) { return Mono.just(false); }
        @Override public Mono<Boolean> existsById(Publisher<String> id) { return Mono.just(false); }
        @Override public Flux<QuizSchedule> findAll() { return Flux.empty(); }
        @Override public Flux<QuizSchedule> findAll(Sort sort) { return Flux.empty(); }
        @Override public Flux<QuizSchedule> findAllById(Iterable<String> ids) { return Flux.empty(); }
        @Override public Flux<QuizSchedule> findAllById(Publisher<String> ids) { return Flux.empty(); }
        @Override public Mono<Long> count() { return Mono.just(0L); }
        @Override public Mono<Void> deleteById(String id) { return Mono.empty(); }
        @Override public Mono<Void> deleteById(Publisher<String> id) { return Mono.empty(); }
        @Override public Mono<Void> delete(QuizSchedule e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAllById(Iterable<? extends String> ids) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Iterable<? extends QuizSchedule> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll(Publisher<? extends QuizSchedule> e) { return Mono.empty(); }
        @Override public Mono<Void> deleteAll() { return Mono.empty(); }
        @Override public <S extends QuizSchedule> Mono<S> insert(S e) { return Mono.just(e); }
        @Override public <S extends QuizSchedule> Flux<S> insert(Iterable<S> e) { return Flux.fromIterable(e); }
        @Override public <S extends QuizSchedule> Flux<S> insert(Publisher<S> e) { return Flux.from(e); }
        @Override public <S extends QuizSchedule> Mono<S> findOne(Example<S> e) { return Mono.empty(); }
        @Override public <S extends QuizSchedule> Flux<S> findAll(Example<S> e) { return Flux.empty(); }
        @Override public <S extends QuizSchedule> Flux<S> findAll(Example<S> e, Sort s) { return Flux.empty(); }
        @Override public <S extends QuizSchedule> Mono<Long> count(Example<S> e) { return Mono.just(0L); }
        @Override public <S extends QuizSchedule> Mono<Boolean> exists(Example<S> e) { return Mono.just(false); }
        @Override public <S extends QuizSchedule, R> Publisher<R> findBy(Example<S> e, Function<FluentQuery.ReactiveFluentQuery<S>, Publisher<R>> q) { return Mono.empty(); }
    }

    // ─────────────────────────────────────────────
    // AuditLogService — конкретный класс, расширяем
    // ─────────────────────────────────────────────
    static AuditLogService noopAudit() {
        return new AuditLogService(null) {
            @Override
            public Mono<Void> log(String initiatorId, String action,
                                  String targetCollection, String targetId) {
                return Mono.empty();
            }
        };
    }
}
