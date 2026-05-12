plugins {
    java
    application
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.quizbot"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass.set("com.quizbot.QuizBotApplication")
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    // Spring Framework 7
    implementation(platform("org.springframework:spring-framework-bom:7.0.0"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-webflux")
    implementation("org.springframework:spring-web")

    // Spring Data MongoDB 
    implementation(platform("org.springframework.data:spring-data-bom:2025.0.0"))
    implementation("org.springframework.data:spring-data-mongodb")

    // Reactor Netty — embedded HTTP server
    implementation("io.projectreactor.netty:reactor-netty-http:1.2.6")

    // MongoDB reactive driver
    implementation("org.mongodb:mongodb-driver-reactivestreams:5.4.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
