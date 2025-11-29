import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Logging
import java.io.File

plugins {
    kotlin("jvm")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("nu.studer.jooq") version "9.0"
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jooq:jooq:3.19.9")
    implementation("org.jooq:jooq-kotlin:3.19.9")
    implementation("org.postgresql:postgresql:42.7.4")
    jooqGenerator("org.postgresql:postgresql:42.7.4")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// --- .env ---
val dotenvFile = rootProject.file(".env")
val envVars = mutableMapOf<String, String>()
if (dotenvFile.exists()) {
    dotenvFile.readLines().forEach { line ->
        if (line.isNotBlank() && !line.trim().startsWith("#") && line.contains("=")) {
            val parts = line.split("=", limit = 2).map { it.trim() }
            val k = parts[0]
            val v = parts.getOrNull(1) ?: ""
            envVars[k] = v
        }
    }
}

fun env(name: String, default: String? = null): String =
    envVars[name] ?: System.getenv(name) ?: default
    ?: throw IllegalStateException("Falta: $name")

// --- Configs ---
// --- .env (mesmo)
val dbHost = env("DB_HOST")
val dbPort = env("DB_PORT", "7061")
val dbName = env("DB_NAME")
val dbUser = env("DB_USER")
val dbPassword = env("DB_PASSWORD")
val dbSchema = env("DB_SCHEMA", "public")
val jooqCertsDir = project.projectDir.resolve("src/main/jooq/certs")
if (!jooqCertsDir.exists()) {
    throw GradleException("Diretório de certificados não existe: $jooqCertsDir")
}

val rootCertsDir = rootProject.projectDir.resolve("certs")
rootCertsDir.mkdirs()

val sourceCerts = projectDir.resolve("src/main/resources/certs")
if (sourceCerts.exists()) {
    sourceCerts.listFiles()?.forEach { file ->
        file.copyTo(rootCertsDir.resolve(file.name), overwrite = true)
    }
}

val clientCrt = jooqCertsDir.resolve("client.crt")
val clientKey = jooqCertsDir.resolve("client.key")
val caCrt = jooqCertsDir.resolve("ca.crt")

if (!clientCrt.exists() || !clientKey.exists() || !caCrt.exists()) {
    throw GradleException("Certificados ausentes em $jooqCertsDir")
}

// --- URL com caminhos absolutos ---
val dbUrl = buildString {
    append("jdbc:postgresql://$dbHost:$dbPort/$dbName")
    append("?ssl=true")
    append("&sslmode=require")
    append("&sslfactory=org.postgresql.ssl.LibPQFactory")
    append("&sslcert=").append(clientCrt.absolutePath.replace("\\", "/"))
    append("&sslkey=").append(clientKey.absolutePath.replace("\\", "/"))
    append("&sslrootcert=").append(caCrt.absolutePath.replace("\\", "/"))
}

// --- JOOQ ---
jooq {
    version.set("3.19.9")
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)
            jooqConfiguration.apply {
                logging = Logging.WARN
                jdbc = Jdbc().apply {
                    driver = "org.postgresql.Driver"
                    url = dbUrl
                    user = dbUser
                    password = dbPassword
                }
                generator = Generator().apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database = Database().apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = dbSchema
                        includes = ".*"
                    }
                    generate = Generate().apply {
                        isDaos = true
                        isPojos = true
                        isRecords = true
                    }
                    target = org.jooq.meta.jaxb.Target().apply {
                        packageName = "studio.styx.erisbot.generated"
                        directory = "src/main/generated"
                    }
                }
            }
        }
    }
}