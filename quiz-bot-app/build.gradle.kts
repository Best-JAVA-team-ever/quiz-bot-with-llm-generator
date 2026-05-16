val springVersion = "7.0.0"

dependencies {
    implementation(project(":quiz-bot-api"))
    implementation(project(":quiz-bot-core"))
    implementation(project(":quiz-bot-persistence"))
    
    implementation("org.springframework:spring-webmvc:$springVersion")
    implementation("org.eclipse.jetty:jetty-server:12.0.14")
    implementation("ch.qos.logback:logback-classic:1.5.12")
}

tasks.getByName<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "com.quizbot.app.QuizBotApplication"
    }
}
