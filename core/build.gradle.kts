val springVersion = "7.0.0"
val springDataVersion = "5.0.0"

dependencies {
    implementation("org.springframework:spring-web:$springVersion")
    implementation("org.springframework.data:spring-data-mongodb:$springDataVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")
}
