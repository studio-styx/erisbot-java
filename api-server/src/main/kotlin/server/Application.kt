package server

import io.ktor.client.* // Importante
import io.ktor.client.engine.cio.* // Importante (ou Apache/OkHttp)
import io.ktor.client.plugins.contentnegotiation.* // Importante
import io.ktor.serialization.kotlinx.json.* // Importante
import io.ktor.client.plugins.* // Para HttpTimeout
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation // Alias para não confundir com o do Client
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import server.core.security.configureSecurity
import server.routes.auth.authRoutes
import server.routes.v2.v2Routes
import server.routes.web.webProtectedRoutes
import server.routes.web.webPublicRoutes
import settings.Settings
import studio.styx.erisbot.generated.tables.references.COMMAND
import studio.styx.erisbot.generated.tables.references.USER
import kotlin.time.Duration.Companion.seconds

@Serializable
data class StatusResponse(
    val servers: Int,
    val users: Int,
    val usersMediaPerServer: Double,
    val version: String,
    val usersInDb: Int,
    val commands: Int,
)

fun main(jda: JDA, dsl: DSLContext) {
    // 1. CRIAÇÃO DO CLIENTE HTTP (SINGLETON)
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
    }

    embeddedServer(Netty, port = 8080) {
        // 2. GARANTIR QUE O CLIENTE FECHE AO DERRUBAR O SERVIDOR
        environment.monitor.subscribe(ApplicationStopping) {
            httpClient.close()
        }

        install(RateLimit) {
            register(RateLimitName("web_public")) { rateLimiter(limit = 1000, refillPeriod = 60.seconds) }
            register(RateLimitName("web_protected")) { rateLimiter(limit = 200, refillPeriod = 60.seconds) }
            register(RateLimitName("v2")) { rateLimiter(limit = 50, refillPeriod = 60.seconds) }
            register(RateLimitName("webhooks")) { rateLimiter(limit = 2000, refillPeriod = 60.seconds) }
            register(RateLimitName("user_auth")) { rateLimiter(limit = 20, refillPeriod = 60.seconds) }
        }

        install(ServerContentNegotiation) {
            json()
        }

        configureSecurity()

        routing {
            webPublicRoutes(jda, dsl)
            webProtectedRoutes(jda, dsl)
            v2Routes(jda, dsl)

            // 3. PASSANDO O CLIENTE PARA A ROTA
            authRoutes(dsl, httpClient)

            get("/") {
                val countServers = jda.guilds.size
                val countUsers = jda.guilds.sumOf { guild -> guild.memberCount }
                val version = Settings.app.getVersion()
                val countUsersInDb = dsl.fetchCount(USER)
                val commandsCount = dsl.fetchCount(COMMAND)

                val response = StatusResponse(
                    servers = countServers,
                    users = countUsers,
                    usersMediaPerServer = countUsers.toDouble() / countServers,
                    version = version,
                    usersInDb = countUsersInDb,
                    commands = commandsCount
                )
                call.respond(response)
            }
        }

    }.start(wait = false)
}