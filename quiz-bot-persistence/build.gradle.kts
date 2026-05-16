val springVersion = "7.0.0"
val springDataVersion = "5.0.0"

dependencies {
    implementation(project(":quiz-bot-core"))
    implementation("org.springframework.data:spring-data-mongodb:$springDataVersion")
    implementation("org.mongodb:mongodb-driver-sync:5.2.1")
}
