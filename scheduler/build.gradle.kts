plugins {
    kotlin("jvm")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common-discord"))
    implementation(project(":erisbot"))
    implementation(project(":database"))
    implementation(project(":redisbridge"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.springframework.boot:spring-boot-starter")

    implementation("net.dv8tion:JDA:6.1.0")

    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.postgresql:postgresql:42.7.4")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

