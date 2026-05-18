plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

val springVersion = "7.0.0"

dependencies {
    implementation(project(":quiz-bot-api"))
    implementation(project(":quiz-bot-core"))
    implementation(project(":quiz-bot-persistence"))
    
    implementation("org.springframework:spring-context:$springVersion")
    implementation("org.springframework:spring-webflux:$springVersion")
    implementation("io.projectreactor.netty:reactor-netty-http:1.2.0")
    implementation("ch.qos.logback:logback-classic:1.5.12")
}

tasks.shadowJar {
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.quizbot.app.QuizBotApplication"
    }
}

tasks.getByName<Jar>("jar") {
    enabled = false // Disable standard jar
}
