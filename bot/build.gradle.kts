plugins {
    id("org.asciidoctor.jvm.convert") version "4.0.0"
}

repositories {
    maven { url = uri("https://repo.spring.io/snapshot") }
    mavenCentral()
}

val asciidoctorExt: Configuration by configurations.creating

val springVersion = "7.0.0"

dependencies {
    implementation(project(":core"))
    implementation(project(":infrastructure"))
    implementation("org.springframework:spring-webflux:$springVersion")
    implementation("org.springframework.data:spring-data-mongodb:5.0.0")
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    implementation("org.telegram:telegrambots-longpolling:7.11.0")
    implementation("org.telegram:telegrambots-client:7.11.0")

    // 3.0.4 uses AsciidoctorJ 2.x, compatible with the Gradle plugin 4.0.0
    asciidoctorExt("org.springframework.restdocs:spring-restdocs-asciidoctor:3.0.4")

    // 4.0.0-SNAPSHOT fixes the Spring 7 HttpHeaders incompatibility for test runtime
    testImplementation("org.springframework.restdocs:spring-restdocs-restassured:4.0.0-SNAPSHOT")
    testImplementation("io.rest-assured:rest-assured:5.5.0")
    testImplementation("io.projectreactor.netty:reactor-netty-http:1.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.12.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.0")
}

val snippetsDir = layout.buildDirectory.dir("generated-snippets")

tasks.test {
    outputs.dir(snippetsDir)
}

tasks.asciidoctor {
    inputs.dir(snippetsDir)
    dependsOn(tasks.test)
    configurations("asciidoctorExt")
    attributes(mapOf("snippets" to snippetsDir.get().asFile))
}
