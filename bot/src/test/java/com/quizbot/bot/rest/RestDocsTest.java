package com.quizbot.bot.rest;

import com.quizbot.core.domain.Role;
import com.quizbot.core.domain.Users;
import com.quizbot.core.service.UserService;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.restdocs.ManualRestDocumentation;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static io.restassured.RestAssured.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.document;
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.documentationConfiguration;



class RestDocsTest {

    private static DisposableServer server;
    private static int port;

    static final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    static {
        meterRegistry.counter("llm.api.calls", "operation", "generate_hint", "status", "success").increment();
        meterRegistry.counter("llm.api.calls", "operation", "generate_hint", "status", "error").increment();
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(meterRegistry.timer("llm.api.duration", "operation", "generate_hint", "status", "success"));
        meterRegistry.summary("llm.api.tokens", "operation", "generate_hint").record(512);
    }

    private final ManualRestDocumentation restDocumentation = new ManualRestDocumentation();
    private RequestSpecification spec;

    @BeforeAll
    static void startServer() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);
        HttpHandler handler = WebHttpHandlerBuilder.applicationContext(context).build();
        server = HttpServer.create().port(0)
                .handle(new ReactorHttpHandlerAdapter(handler))
                .bindNow();
        port = server.port();
    }

    @AfterAll
    static void stopServer() {
        server.disposeNow();
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        this.restDocumentation.beforeTest(getClass(), testInfo.getTestMethod().get().getName());
        this.spec = new RequestSpecBuilder()
                .addFilter(documentationConfiguration(restDocumentation))
                .build();
    }

    @AfterEach
    void tearDown() {
        this.restDocumentation.afterTest();
    }

    @Test
    void healthcheck() {
        given(this.spec)
                .filter(document("healthcheck",
                        responseFields(
                                fieldWithPath("status").description("Статус сервиса: `UP` или `DOWN`"),
                                fieldWithPath("authors").description("Список авторов проекта")
                        )))
                .when().get("http://localhost:" + port + "/healthcheck")
                .then().statusCode(200);
    }

    @Test
    void getUsers_success() {
        given(this.spec)
                .header("X-API-KEY", "test-api-key")
                .filter(document("get-users",
                        requestHeaders(
                                headerWithName("X-API-KEY").description("Ключ API администратора")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("Telegram ID пользователя"),
                                fieldWithPath("[].role").description("Роль пользователя: `ADMIN` или `USER`"),
                                fieldWithPath("[].registeredAt").description("Дата и время регистрации (ISO-8601)")
                        )))
                .when().get("http://localhost:" + port + "/users")
                .then().statusCode(200);
    }

    @Test
    void getUsers_forbidden() {
        given(this.spec)
                .filter(document("get-users-forbidden"))
                .when().get("http://localhost:" + port + "/users")
                .then().statusCode(403);
    }

    @Test
    void getLlmStats_success() {
        given(this.spec)
                .header("X-API-KEY", "test-api-key")
                .filter(document("get-llm-stats",
                        requestHeaders(
                                headerWithName("X-API-KEY").description("Ключ API администратора")
                        ),
                        responseFields(
                                fieldWithPath("[].operation").description("Идентификатор операции LLM"),
                                fieldWithPath("[].successCount").description("Количество успешных вызовов"),
                                fieldWithPath("[].errorCount").description("Количество неудачных вызовов"),
                                fieldWithPath("[].meanDurationMs").description("Среднее время выполнения (мс); 0 если вызовов не было"),
                                fieldWithPath("[].maxDurationMs").description("Максимальное время выполнения (мс); 0 если вызовов не было"),
                                fieldWithPath("[].totalTokens").description("Суммарное количество токенов; 0 если вызовов не было"),
                                fieldWithPath("[].meanTokens").description("Среднее количество токенов за вызов; 0 если вызовов не было")
                        )))
                .when().get("http://localhost:" + port + "/llm-stats")
                .then().statusCode(200);
    }

    @Test
    void getLlmStats_forbidden() {
        given(this.spec)
                .filter(document("get-llm-stats-forbidden"))
                .when().get("http://localhost:" + port + "/llm-stats")
                .then().statusCode(403);
    }

    @Configuration
    @EnableWebFlux
    static class TestConfig {

        @Bean
        HealthController healthController() {
            return new HealthController();
        }

        @Bean
        UserService userService() {
            return new UserService(null, null, "") {
                @Override
                public Flux<Users> getAllUsers() {
                    return Flux.just(Users.create(123456789L, Role.USER));
                }
            };
        }

        @Bean
        AdminRestController adminRestController(UserService userService) {
            return new AdminRestController(userService, "test-api-key");
        }

        @Bean
        LlmStatsController llmStatsController() {
            return new LlmStatsController(meterRegistry, "test-api-key");
        }
    }
}
