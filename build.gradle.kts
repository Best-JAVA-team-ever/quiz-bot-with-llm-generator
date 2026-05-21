plugins {
    java
}

allprojects {
    group = "com.quizbot"
    version = "1.0.0-RELEASE"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    val springVersion = "7.0.0"

    dependencies {
        implementation("org.springframework:spring-context:$springVersion")
        implementation("org.springframework:spring-web:$springVersion")
        implementation("io.projectreactor:reactor-core:3.7.0")
        implementation("org.slf4j:slf4j-api:2.0.16")
        implementation("io.micrometer:micrometer-core:1.14.2")

        testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
