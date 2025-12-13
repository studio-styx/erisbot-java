package server.routes.web.protectedWeb.auth

import discord.extensions.jda.users.getOrRetrieveUserAsync
import discord.extensions.jda.users.getOrRetrieveUserOrNullAsync
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import server.core.extensions.database.toFullUserResponse
import studio.styx.erisbot.generated.tables.references.USER

@Serializable
data class GetMeResponse(
    val database: FullUserResponse,
    val discord: DiscordUserResponse
)

@Serializable
data class DiscordUserResponse(
    val id: String,
    val username: String,
    val discriminator: String,
    val avatar: String,
    val displayName: String
)

@Serializable
data class FullUserResponse(
    val id: String,
    val money: Double,          // Prisma Decimal(12, 2)
    val xp: Int,
    val contractId: Int?,
    val afkReasson: String?,    // Mantive o nome do seu schema (com erro de digitação mesmo)
    val afkTime: String?,       // DateTime convertido para String ISO
    val dmNotification: Boolean,
    val activePetId: Int?,
    val blacklist: String?,     // Json cru ou null
    val createdAt: String,
    val updatedAt: String,
    val showNameInPresence: Boolean,
    val gender: String?,        // Enum convertido para String
    val readFootballBetTerms: Boolean,
    val acceptedFootballTermsAt: String?,
    val favoriteTeamId: Long?   // Prisma BigInt
)
fun Route.getMeRoute(dsl: DSLContext, jda: JDA) {
    get("/me") {
        // Se chegar aqui, o token JÁ é válido. Se fosse inválido, parava no 'authenticate'
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.payload?.getClaim("user_id")?.asString()

        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido ou sem user_id"))
            return@get
        }

        // 2. Buscar o usuário no banco de dados para confirmar registro
        val userRecord = dsl.selectFrom(USER)
            .where(USER.ID.eq(userId))
            .fetchOne()

        if (userRecord == null) {
            // O token é válido, mas o usuário não existe mais no banco (foi deletado?)
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Usuário não encontrado"))
            return@get
        }

        val jdaUser = jda.getOrRetrieveUserOrNullAsync(userId)

        if (jdaUser == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Usuário não encontrado"))
            return@get
        }

        val databaseUser = userRecord.toFullUserResponse()
        val discordUser = DiscordUserResponse(
            id = jdaUser.id,
            username = jdaUser.name,
            discriminator = jdaUser.discriminator,
            avatar = jdaUser.effectiveAvatarUrl,
            displayName = jdaUser.effectiveName
        )
        val response = GetMeResponse(databaseUser, discordUser)
        call.respond(response)
    }
}