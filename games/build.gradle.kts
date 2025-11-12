plugins {
    kotlin("jvm")
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":database"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}