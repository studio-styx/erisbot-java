package studio.styx.erisbot.discord.features.commands.football.subCommands

import database.extensions.getOrCreateUser
import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.reply.rapidContainerReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.generated.tables.references.FOOTBALLTEAM
import studio.styx.erisbot.generated.tables.references.USER
import studio.styx.schemaEXtended.core.schemas.NumberSchema

class SetFavoriteTeamCommand(
    private val event: SlashCommandInteractionEvent,
    private val dsl: DSLContext
) {
    private companion object {
        private val TEAM_SCHEMA = NumberSchema()
            .min(1)
            .minError("O id do time deve ser maior que 0")
            .parseError("O id do time deve ser um número")
            .coerce()

        private const val ERROR_TEAM_NOT_FOUND = "error"
        private const val ERROR_ALREADY_FAVORITE = "denied"
        private const val SUCCESS_ICON = "success"
    }

     suspend fun execute() {
        val teamId = TEAM_SCHEMA.parseOrThrow(event.getOption("team")?.asString).toLong()

        event.deferReply().await()

        val team = getTeamById(teamId)
        if (team == null) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get(ERROR_TEAM_NOT_FOUND)} | Eu não consegui encontrar esse time!"
            )
            return
        }

        val user = dsl.getOrCreateUser(event.user.id)
        if (user.favoriteteamid == teamId) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get(ERROR_ALREADY_FAVORITE)} | Você já está torcendo para esse time!"
            )
            return
        }

        val favoritesCount = updateAndGetFavoritesCount(user.id!!, teamId)

        sendSuccessResponse(team.get(FOOTBALLTEAM.NAME)!!, favoritesCount)
    }

    private suspend fun getTeamById(teamId: Long) = withContext(Dispatchers.IO) {
        dsl.select(FOOTBALLTEAM.ID, FOOTBALLTEAM.NAME)
            .from(FOOTBALLTEAM)
            .where(FOOTBALLTEAM.ID.eq(teamId))
            .fetchOne()
    }

    private suspend fun updateAndGetFavoritesCount(userId: String, teamId: Long): Int = withContext(Dispatchers.IO) {
        dsl.transactionResult { config ->
            val transactionalDsl = config.dsl()

            // Atualiza o time favorito do usuário
            transactionalDsl.update(USER)
                .set(USER.FAVORITETEAMID, teamId)
                .where(USER.ID.eq(userId))
                .execute()

            // Obtém a nova contagem
            transactionalDsl.selectCount()
                .from(USER)
                .where(USER.FAVORITETEAMID.eq(teamId))
                .fetchOne { it.value1() } ?: 1
        }
    }

    private suspend fun sendSuccessResponse(teamName: String, favoritesCount: Int) {
        val message = buildString {
            append("${Icon.static.get(SUCCESS_ICON)} | Sucesso ao definir seu time para $teamName, sabia que ")
            append(
                if (favoritesCount <= 1) "você é o primeiro a torcer para esse time?"
                else "outras ${favoritesCount - 1} pessoas torcem para esse time?"
            )
        }

        event.rapidContainerReply(Colors.SUCCESS, message)
    }
}