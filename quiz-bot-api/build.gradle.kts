val springVersion = "7.0.0"

dependencies {
    implementation(project(":quiz-bot-core"))
    implementation("org.springframework:spring-webmvc:$springVersion")
    implementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
}
