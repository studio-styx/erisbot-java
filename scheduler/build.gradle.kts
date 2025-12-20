plugins {
    kotlin("jvm")
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common-discord"))
    implementation(project(":redis"))

    implementation(project(":database"))
    implementation(project(":redisbridge"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.postgresql:postgresql:42.7.4")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")

    implementation("redis.clients:jedis:4.4.0")

    implementation("net.dv8tion:JDA:6.2.0")
    implementation("club.minnced:jda-ktx:0.13.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.bootJar {
    enabled = false // Desativa a criação de executável para este módulo
}

tasks.jar {
    enabled = true // Garante que ele gere um .jar comum para ser usado pelo módulo principal
}

tasks.test {
    useJUnitPlatform()
}