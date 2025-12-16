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
import studio.styx.erisbot.functions.utils.verifyPetName
import studio.styx.erisbot.generated.tables.references.USERPET

class PetChangeName(
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
        
        val newName = event.getOption("name")!!.asString

        val nameErros = verifyPetName(newName)

        if (nameErros.isNotEmpty()) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("error")} | ${nameErros.joinToString("\n")}"
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

        if (pet.name == newName) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("denied")} | O novo nome é o mesmo do nome do pet!"
            )
            return
        }

        withContext(Dispatchers.IO) {
            dsl.transaction { config ->
                val tx = config.dsl()

                tx.update(USERPET)
                    .set(USERPET.NAME, newName)
                    .where(USERPET.ID.eq(pet.id))
                    .execute()
                tx.createLog(CreateLogData(
                    userId = event.user.id,
                    message = "mudou o nome do pet ${pet.name} (ID: ${pet.id}) para ${newName}.",
                    tags = listOf("pet", "name", "change", "change name", pet.id.toString())
                )).insert()
            }
        }

        event.rapidContainerReply(
            Colors.DANGER,
            "Nome do pet alterado de **${pet.name}** para **${newName}**!"
        )
    }
}