package studio.styx.erisbot.discord.features.commands.pets.subCommands

import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.reply.rapidContainerReply
import kotlinx.coroutines.Dispatchers
import org.jooq.impl.DSL
import kotlinx.coroutines.withContext
import menus.pets.AdoptionCenterMenuData
import menus.pets.adoptPetMenu
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.generated.tables.references.ADOPTIONCENTER
import studio.styx.erisbot.generated.tables.references.PERSONALITYTRAIT
import studio.styx.erisbot.generated.tables.references.PET
import studio.styx.erisbot.generated.tables.references.PETSKILL
import studio.styx.erisbot.generated.tables.references.USERPET
import studio.styx.erisbot.generated.tables.references.USERPETPERSONALITY
import studio.styx.erisbot.generated.tables.references.USERPETSKILL
import java.time.LocalDateTime

class PetAdoptionCenterCommand(
    private val event: SlashCommandInteractionEvent,
    private val dsl: DSLContext
) {
    suspend fun execute() {
        event.deferReply(true).await()

        // 1. Defina os campos Multiset separadamente para manter a tipagem forte
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

        // 2. Adicionando o Multiset de Skills
        val skillsField = DSL.multiset(
            dsl.select(PETSKILL.asterisk()) // Seleciona os dados da habilidade
                .from(USERPETSKILL)
                .innerJoin(PETSKILL).on(USERPETSKILL.SKILLID.eq(PETSKILL.ID)) // Junta tabela de ligação com a definição
                .where(USERPETSKILL.USERPETID.eq(ADOPTIONCENTER.USERPETID))
        ).convertFrom { result ->
            // Retorna List<SkillRecord>
            result.into(PETSKILL)
        }

        // 3. Executa a Query
        val availablePets = withContext(Dispatchers.IO) {
            dsl.select(
                ADOPTIONCENTER.asterisk(),
                petDetailsField,
                traitsField,
                skillsField
            )
                .from(ADOPTIONCENTER)
                .where(ADOPTIONCENTER.DELETEDAT.isNull)
                .and(ADOPTIONCENTER.DELETEIN.greaterThan(LocalDateTime.now()))
                .fetch { record ->
                    // 4. Recupera os dados usando as variáveis de campo (Type-Safe!)
                    // O jOOQ sabe exatamente o tipo que volta aqui
                    val petData = record[petDetailsField]
                    val traits = record[traitsField]
                    val skills = record[skillsField]

                    val adoption = record.into(ADOPTIONCENTER)

                    if (petData != null) {
                        AdoptionCenterMenuData(
                            adoptionCenter = adoption,
                            userPet = petData.first,
                            pet = petData.second,
                            skills = skills,
                            personality = traits
                        )
                    } else null
                }
                .filterNotNull()
        }

        if (availablePets.isEmpty()) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("error")} | Nenhum pet foi encontrado para a adoção!"
            )
            return
        }

        val menu = adoptPetMenu(availablePets)
        event.hook.editOriginalComponents(menu).useComponentsV2().await()
    }
}