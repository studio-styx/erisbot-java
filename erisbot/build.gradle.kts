plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common-discord"))
    implementation(project(":database"))
    implementation(project(":games"))
    implementation(project(":scheduler"))
    implementation(project(":redisbridge"))
    implementation(project(":gemini-service"))
    implementation(project(":redis"))

    implementation("net.dv8tion:JDA:6.1.0")
    implementation("org.reflections:reflections:0.10.2")
    implementation("io.github.cdimascio:java-dotenv:5.2.2")
    implementation("org.json:json:20240303")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.google.genai:google-genai:1.0.0")

    implementation("studio.styx.schemaEXtended:SchemaEXtended:1.2.0")

    implementation("redis.clients:jedis:4.4.0")

    runtimeOnly("org.postgresql:postgresql:42.7.4")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
