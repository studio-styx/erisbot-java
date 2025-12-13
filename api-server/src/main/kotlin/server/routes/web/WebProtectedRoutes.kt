package server.routes.web

import io.ktor.http.HttpMethod
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import server.routes.web.protectedWeb.auth.getMeRoute

fun Route.webProtectedRoutes(jda: JDA, dsl: DSLContext) {
    route("/web/protected") {
        install(CORS) {
            allowHost("erisbot.squareweb.app", schemes = listOf("https"))
            allowHost("localhost:3000", schemes = listOf("http"))

            // permita métodos usados pelo site
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Patch)

            // permita headers comuns de requisição JSON / autorização
            allowHeader(io.ktor.http.HttpHeaders.ContentType)
            allowHeader(io.ktor.http.HttpHeaders.Authorization)

            allowCredentials = true
        }
        rateLimit(RateLimitName("web_protected")) {
            authenticate("auth-jwt") {
                getMeRoute(dsl, jda)
            }
        }
    }

}