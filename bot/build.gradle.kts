val springVersion = "7.0.0"

dependencies {
    implementation(project(":core"))
    implementation(project(":infrastructure"))
    implementation("org.springframework:spring-webflux:$springVersion")
    implementation("org.springframework.data:spring-data-mongodb:5.0.0")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    implementation("org.telegram:telegrambots-longpolling:7.11.0")
    implementation("org.telegram:telegrambots-client:7.11.0")
}
