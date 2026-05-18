val springVersion = "7.0.0"

dependencies {
    implementation(project(":quiz-bot-core"))
    implementation(project(":quiz-bot-persistence"))
    implementation("org.springframework:spring-webflux:$springVersion")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    implementation("org.telegram:telegrambots-longpolling:7.11.0")
    implementation("org.telegram:telegrambots-client:7.11.0")
}
