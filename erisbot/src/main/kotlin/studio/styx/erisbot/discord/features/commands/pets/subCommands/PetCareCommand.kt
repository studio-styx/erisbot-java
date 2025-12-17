package studio.styx.erisbot.discord.features.commands.pets.subCommands

import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.reply.rapidContainerReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import org.jooq.impl.DSL
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.generated.tables.references.ADOPTIONCENTER
import studio.styx.erisbot.generated.tables.references.PERSONALITYTRAIT
import studio.styx.erisbot.generated.tables.references.PET
import studio.styx.erisbot.generated.tables.references.USERPET
import studio.styx.erisbot.generated.tables.references.USERPETPERSONALITY
import studio.styx.schemaEXtended.core.schemas.NumberSchema

class PetCareCommand(
    private val event: SlashCommandInteractionEvent,
    private val dsl: DSLContext
) {
    val SCHEMA = NumberSchema()
        .parseError("Insira um id de pet válido")
        .minError("Insira um id de pet maior que 1")
        .min(1)
        .integer()
        .coerce()

    suspend fun execute() {
        event.deferReply(true).await()

        val petId = SCHEMA.parseOrThrow(event.getOption("pet")?.asString).toInt()

        val petDetailsField = DSL.multiset(
            dsl.select(USERPET.asterisk(), PET.asterisk())
                .from(USERPET)
                .innerJoin(PET).on(USERPET.PETID.eq(PET.ID))
                .where(USERPET.ID.eq(ADOPTIONCENTER.USERPETID))
        ).convertFrom { result ->
            // Retorna Pair<UserpetRecord, PetRecord>?
            result.map { it.into(USERPET) to it.into(PET) }.firstOrNull()
        }

        val traitsField = DSL.multiset(
            dsl.select(PERSONALITYTRAIT.asterisk())
                .from(USERPETPERSONALITY)
                .innerJoin(PERSONALITYTRAIT).on(USERPETPERSONALITY.TRAITID.eq(PERSONALITYTRAIT.ID))
                .where(USERPETPERSONALITY.USERPETID.eq(ADOPTIONCENTER.USERPETID))
        ).convertFrom { result ->
            // Retorna List<PersonalitytraitRecord>
            result.into(PERSONALITYTRAIT)
        }

        val result = withContext(Dispatchers.IO) {
            dsl.select(USERPET.asterisk(), petDetailsField, traitsField)
                .from(USERPET)
                .where(USERPET.ID.eq(petId))
                .and(USERPET.USERID.eq(event.user.id))
                .and(USERPET.ISDEAD.eq(false))
                .and(USERPET.adoptioncenter.eq(null))
                .fetchOne { record ->
                    val petData = record[petDetailsField]
                    val traits = record[traitsField]

                    if (petData != null) {
                        Triple(
                            petData.first,
                            petData.second,
                            traits
                        )
                    } else {
                        null
                    }
                }
        }

        if (result == null) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Não consegui encontrar o pet!")
            return
        }

        val (userPet, pet, personality) = result


    }
}