plugins {
    kotlin("jvm")
    id("org.springframework.boot") version "3.3.4" apply false
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":database"))
    implementation(project(":redisbridge"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.springframework.boot:spring-boot-starter")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

