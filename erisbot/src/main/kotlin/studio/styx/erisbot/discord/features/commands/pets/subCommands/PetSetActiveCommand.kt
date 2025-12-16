package studio.styx.erisbot.discord.features.commands.pets.subCommands

import database.dtos.log.CreateLogData
import database.extensions.createLog
import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.reply.rapidContainerReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.generated.tables.references.USER
import studio.styx.erisbot.generated.tables.references.USERPET

class PetSetActiveCommand(
    private val event: SlashCommandInteractionEvent,
    private val dsl: DSLContext
) {
    suspend fun execute() {
        val petId = event.getOption("pet")?.asString?.toIntOrNull() ?: run {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("error")} | Insira um id de pet válido!"
            )
            return
        }

        event.deferReply().await()

        val pet = withContext(Dispatchers.IO) {
            dsl.selectFrom(USERPET)
                .where(USERPET.ID.eq(petId))
                .and(USERPET.USERID.eq(event.user.id))
                .and(USERPET.ISDEAD.eq(false))
                .and(USERPET.adoptioncenter.eq(null))
                .fetchOne()
        } ?: run {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("denied")} | Eu não consegui encontrar esse pet!"
            )
            return
        }

        withContext(Dispatchers.IO) {
            dsl.transaction { config ->
                val tx = config.dsl()

                tx.update(USER)
                    .set(USER.ACTIVEPETID, pet.id)
                    .where(USER.ID.eq(event.user.id))
                    .execute()
                tx.createLog(CreateLogData(
                    userId = event.user.id,
                    message = "definido o pet ${pet.name} (ID: ${pet.id}) como ativo.",
                    tags = listOf("pet", "active", "set", "USERPETID_${pet.id}")
                )).insert()
            }
        }

        event.rapidContainerReply(
            Colors.SUCCESS,
            "${Icon.static.get("success")} | Você definiu seu pet ${pet.name} (ID: ${pet.id}) como ativo!"
        )
    }
}