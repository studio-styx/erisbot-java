package server.core.security.frontOnly

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.jooq.DSLContext

fun Route.frontMiddleware() {
    intercept(ApplicationCallPipeline.Call) {
        val header = call.request.headers["Authorization"]
        if (header == null) {
            call.respond(HttpStatusCode.Unauthorized, "Missing Authorization")
            finish()
            return@intercept
        }

        if (!header.equals(System.getenv("FRONT_SECRET"))) {
            call.respond(HttpStatusCode.Unauthorized, "Only frontend can use this route")
            finish()
            return@intercept
        }
    }
}