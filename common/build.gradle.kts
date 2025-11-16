plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // .env reader
    implementation("io.github.cdimascio:java-dotenv:5.2.2")

    implementation("studio.styx.sx:SchemaEXtended:1.1.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
