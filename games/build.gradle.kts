plugins {
    kotlin("jvm")
    id("org.springframework.boot")              // versão vem do root
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
    implementation(project(":database"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
    implementation("club.minnced:jda-ktx:0.13.0")
    implementation("net.dv8tion:JDA:6.2.0")

    implementation("org.jooq:jooq:3.19.9")
    implementation("org.jooq:jooq-kotlin:3.19.9")
    implementation("org.postgresql:postgresql:42.7.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.bootJar {
    enabled = false // Desativa a criação de executável para este módulo
}

tasks.jar {
    enabled = true // Garante que ele gere um .jar comum para ser usado pelo módulo principal
}

kotlin {
    jvmToolchain(21)
}