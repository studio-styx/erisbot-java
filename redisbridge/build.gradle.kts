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

    // Cliente Redis — escolha um deles (exemplo: Lettuce)
    implementation("io.lettuce:lettuce-core:6.3.1.RELEASE")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
