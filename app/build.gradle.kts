plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

val springVersion = "7.0.0"

dependencies {
    implementation(project(":bot"))
    implementation(project(":core"))
    implementation(project(":infrastructure"))
    
    implementation("org.springframework:spring-context:$springVersion")
    implementation("org.springframework:spring-webflux:$springVersion")
    implementation("io.projectreactor.netty:reactor-netty-http:1.2.0")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    implementation("org.springframework.data:spring-data-mongodb:5.0.0")
    implementation("io.micrometer:micrometer-core:1.14.2")
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
