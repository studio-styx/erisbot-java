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
import studio.styx.erisbot.generated.tables.references.ADOPTIONCENTER
import studio.styx.erisbot.generated.tables.references.USERPET
import java.time.LocalDateTime

class PetReleaseCommand(
    private val event: SlashCommandInteractionEvent,
    private val dsl: DSLContext
) {
    suspend fun execute() {
        val petId = event.getOption("pet")?.asString?.toIntOrNull() ?: run {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("denied")} | Insira um id de pet válido"
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

                tx.insertInto(ADOPTIONCENTER)
                    .set(ADOPTIONCENTER.USERPETID, pet.id)
                    .set(ADOPTIONCENTER.DELETEIN, LocalDateTime.now().plusDays(7))
                    .set(ADOPTIONCENTER.CREATEDAT, LocalDateTime.now())
                    .execute()
                tx.update(USERPET)
                    .set(USERPET.ISPREGNANT, false)
                    .set(USERPET.PREGNANTENDAT, null as LocalDateTime?)
                    .where(USERPET.ID.eq(pet.id))
                    .execute()
                tx.createLog(CreateLogData(
                    userId = event.user.id,
                    message = "botou o pet ${pet.name} (ID: ${pet.id}) para adoção.",
                    tags = listOf("pet", "release", "adoption_center", "USERPETID_${pet.id}")
                )).insert()
            }
        }

        event.rapidContainerReply(
            Colors.SUCCESS,
            "${Icon.static.get("Eris_cry")} | Você botou seu bixinho para adoção! que falta de amor no coração \uD83D\uDC94"
        )
    }
}