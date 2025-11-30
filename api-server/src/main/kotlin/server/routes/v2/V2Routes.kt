package server.routes.v2

import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import server.core.security.v2.auth.v2Auth

fun Route.v2Routes(jda: JDA, dsl: DSLContext) {
    route("/v2") {
        rateLimit(RateLimitName("web_protected")) {
            v2Auth(dsl)
        }
    }

}