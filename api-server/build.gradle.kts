plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.9"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "studio.styx.erisbot"
version = "1.0.0"

application {
    mainClass.set("server.ApplicationKt")
    // para gerar jar/fatJar
    // applicationDefaultJvmArgs = listOf("-Dio.ktor.development=true")
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.9"
val exposedVersion = "0.52.0"

dependencies {
    implementation(project(":common"))
    implementation(project(":common-discord"))
    implementation(project(":database"))
    implementation(project(":redis"))

    // --- KTOR CORE ---
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    // --- JSON ---
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // --- AUTH / JWT ---
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")

    // --- LOGGING ---
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")

    // --- STATUS PAGES (tratamento global de erros) ---
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    // --- JDA ---
    implementation("net.dv8tion:JDA:6.2.0")
    implementation("club.minnced:jda-ktx:0.13.0")

    // --- REDIS ---
    implementation("redis.clients:jedis:4.4.0")

    // --- BANCO DE DADOS ---
    implementation("org.jooq:jooq:3.19.9")

    // jOOQ Kotlin Extensions
    implementation("org.jooq:jooq-kotlin:3.19.9")

    implementation("org.postgresql:postgresql:42.7.4")

    // --- UTILIDADES ---
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // --- TESTES ---
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
}


kotlin {
    jvmToolchain(21)
}
