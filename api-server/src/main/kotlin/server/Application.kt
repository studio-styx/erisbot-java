package server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.routing.routing
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import server.core.security.configureSecurity
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import server.routes.v2.v2Routes
import server.routes.web.webProtectedRoutes
import server.routes.web.webPublicRoutes
import kotlin.time.Duration.Companion.seconds


@Serializable
data class StatusResponse(
    val servers: Int,
    val users: Int,
    val version: String
)

fun main(jda: JDA, dsl: DSLContext) {
    embeddedServer(Netty, port = 8080) {
        install(RateLimit) {
            // registrar diferentes “limiter providers” com nomes
            register(RateLimitName("web_public")) {
                rateLimiter(limit = 1000, refillPeriod = 60.seconds)
            }
            register(RateLimitName("web_protected")) {
                rateLimiter(limit = 200, refillPeriod = 60.seconds)
            }
            register(RateLimitName("v2")) {
                rateLimiter(limit = 50, refillPeriod = 60.seconds)
            }
            register(RateLimitName("webhooks")) {
                rateLimiter(limit = 2000, refillPeriod = 60.seconds)
            }
            register(RateLimitName("user_auth")) {
                rateLimiter(limit = 20, refillPeriod = 60.seconds)
            }
        }

        install(ContentNegotiation) {
            json()  // registra serialização JSON
        }

        configureSecurity()
        routing {
            // Rotas exclusivas do site
            webPublicRoutes(jda, dsl)

            // Rotas do site protegidas por JWT — /web/protected/**
            webProtectedRoutes(jda, dsl)
            // Rotas públicas da API /v2 — só publicação por bots com header X-Authorization
            v2Routes(jda, dsl)

            get("/") {
                val countServers = jda.guilds.size
                val countUsers = jda.guilds.sumOf { guild -> guild.memberCount }
                val version = "1.0.0"

                val response = StatusResponse(
                    servers = countServers,
                    users = countUsers,
                    version = version
                )
                call.respond(response)
            }
        }

    }.start(wait = true)
}