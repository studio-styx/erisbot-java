plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":database"))
    implementation(project(":redis"))

    testImplementation(kotlin("test"))
    implementation("net.dv8tion:JDA:6.1.0")
    implementation("club.minnced:jda-ktx:0.13.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")

    implementation("org.jooq:jooq:3.19.9")
    implementation("org.jooq:jooq-kotlin:3.19.9")

    implementation("org.postgresql:postgresql:42.7.4")

    implementation("io.ktor:ktor-client-core:2.3.9")
    implementation("io.ktor:ktor-client-cio:2.3.9")

    // Para serialização JSON
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.9")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.9")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    implementation("redis.clients:jedis:4.4.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")

    runtimeOnly("org.postgresql:postgresql:42.7.4")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}