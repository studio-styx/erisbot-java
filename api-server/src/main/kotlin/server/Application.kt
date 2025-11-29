package server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import server.core.security.configureSecurity

fun main() {
    embeddedServer(Netty, port = 8080) {
        configureSecurity()
        routing {
            // Rotas exclusivas do site
            route("/web") {

            }

            // Rotas do site protegidas por JWT — /web/protected/**
            route("/web/protected") {
                authenticate("auth-jwt") {
                    get("/me") { /* só com token válido */ }
                    post("/update") { /* idem */ }
                }
            }

            // Rotas públicas da API /v2 — só publicação por bots com header X-Authorization
            route("/v2") {
                intercept(ApplicationCallPipeline.Plugins) {
                    val header = call.request.headers["X-Authorization"]
                    if (header != "seu-token-esperado") {
                        call.respond(HttpStatusCode.Unauthorized, "Missing X-Authorization")
                        finish()
                    }
                }

                get("/public-data") { /* disponível a clientes autorizados via header */ }
                post("/do-something") { /* idem */ }
            }
        }

    }.start(wait = true)
}