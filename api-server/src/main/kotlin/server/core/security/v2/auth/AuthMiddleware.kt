package server.core.security.v2.auth

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.respond
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import org.jooq.DSLContext
import studio.styx.erisbot.generated.tables.records.ApplicationRecord
import studio.styx.erisbot.generated.tables.references.APPLICATION
import java.security.MessageDigest

val AppKey = AttributeKey<ApplicationRecord>("AppKey")

fun Route.v2Auth(dsl: DSLContext) {
    intercept(ApplicationCallPipeline.Call) {
        val header = call.request.headers["X-Authorization"]
        if (header == null) {
            call.respond(HttpStatusCode.Unauthorized, "Missing X-Authorization")
            finish()
            return@intercept
        }

        val hashed = sha256(header)
        val app = dsl.selectFrom(APPLICATION)
            .where(APPLICATION.TOKEN.eq(hashed))
            .fetchOne()

        if (app == null) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid token")
            finish()
            return@intercept
        }

        call.attributes.put(AppKey, app)

    }
}

// util
fun sha256(value: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
