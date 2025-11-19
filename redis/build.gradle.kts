plugins {
    kotlin("jvm") version "2.2.20"
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":common"))

    implementation("redis.clients:jedis:4.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}


tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}