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
        implementation("org.slf4j:slf4j-api:2.0.16")
        
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
