val springVersion = "7.0.0"
val springDataVersion = "5.0.0"

dependencies {
    implementation(project(":core"))
    implementation("org.springframework:spring-webflux:$springVersion")
    implementation("io.projectreactor.netty:reactor-netty-http:1.2.0")
    implementation("org.springframework.data:spring-data-mongodb:$springDataVersion")
    implementation("org.mongodb:mongodb-driver-reactivestreams:5.6.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")
    
    testImplementation("org.springframework:spring-test:$springVersion")
    testImplementation("org.springframework.data:spring-data-mongodb:$springDataVersion")
}
