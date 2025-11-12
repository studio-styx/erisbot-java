import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Logging
import java.util.*

plugins {
    kotlin("jvm")
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("nu.studer.jooq") version "9.0"
}

group = "studio.styx.erisbot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.postgresql:postgresql:42.7.4")
    jooqGenerator("org.postgresql:postgresql:42.7.4")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// --- Carrega variáveis do .env manualmente ---
val dotenvFile = rootProject.file(".env")
val envVars = mutableMapOf<String, String>()

if (dotenvFile.exists()) {
    dotenvFile.forEachLine { line ->
        if (line.isNotBlank() && !line.trim().startsWith("#") && line.contains("=")) {
            val (key, value) = line.split("=", limit = 2)
            envVars[key.trim()] = value.trim()
        }
    }
}

fun env(name: String, default: String) =
    envVars[name] ?: System.getenv(name) ?: default

// --- Usa valores do .env ou defaults ---
val dbUrl = env("DB_URL", "jdbc:postgresql://localhost:5432/devdb")
val dbUser = env("DB_USER", "devuser")
val dbPassword = env("DB_PASSWORD", "devsenha")
val dbSchema = env("DB_SCHEMA", "public")

// --- Configuração do JOOQ ---
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
