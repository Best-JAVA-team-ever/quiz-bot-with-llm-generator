package com.quizbot.core.repository;

import com.quizbot.core.domain.Users;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UsersRepository extends ReactiveMongoRepository<Users, String> {

    // Авторизация по Telegram ID при каждом входящем сообщении
    Mono<Users> findByTelegramId(Long telegramId);

    // Авторизация — только среди неудалённых (защита от дубликатов при регистрации)
    Mono<Users> findByTelegramIdAndDeletedAtIsNull(Long telegramId);

    // GET /users — список всех зарегистрированных пользователей (Admin)
    Flux<Users> findAllByDeletedAtIsNull();

    // Рассылка по глобальному расписанию — только активные пользователи
    Flux<Users> findAllByIsActiveTrueAndDeletedAtIsNull();

    // /upgrade <ID> — поиск пользователя для повышения роли
    Mono<Users> findByIdAndDeletedAtIsNull(String id);
}
