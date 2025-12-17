package studio.styx.erisbot.discord.features.interactions.pets

import database.extensions.getOrCreateUser
import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.reply.rapidContainerReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import menus.pets.AdoptionCenterMenuData
import menus.pets.adoptPetMenu
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Icon
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.generated.tables.references.ADOPTIONCENTER
import studio.styx.erisbot.generated.tables.references.PERSONALITYTRAIT
import studio.styx.erisbot.generated.tables.references.PET
import studio.styx.erisbot.generated.tables.references.PETSKILL
import studio.styx.erisbot.generated.tables.references.USERPET
import studio.styx.erisbot.generated.tables.references.USERPETPERSONALITY
import studio.styx.erisbot.generated.tables.references.USERPETSKILL
import java.time.LocalDateTime

@Component
class AdoptionCenterInteraction : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "pet/adoptionCenter/:action/:number"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val params = CustomIdHelper(customId, event.customId)

        val action = params.get("action")!!

        event.deferEdit().await()

        if (action == "page")
        {
            val page = params.getAsInt("page")!!

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

            val menu = adoptPetMenu(availablePets, page)
            event.hook.editOriginalComponents(menu).await()
        }
        else
        {
            val petId = params.getAsInt("number")!!

            val adoptionPet = withContext(Dispatchers.IO) {
                dsl.select(
                    ADOPTIONCENTER.asterisk(),
                    USERPET.asterisk(),
                )
                    .from(ADOPTIONCENTER)
                    .innerJoin(USERPET).on(ADOPTIONCENTER.USERPETID.eq(USERPET.ID))
                    .where(ADOPTIONCENTER.USERPETID.eq(petId))
                    .and(ADOPTIONCENTER.DELETEDAT.isNull)
                    .and(ADOPTIONCENTER.DELETEIN.greaterThan(LocalDateTime.now()))
                    .fetchOne()
            }

            if (adoptionPet == null) {
                event.rapidContainerReply(
                    Colors.DANGER,
                    "${Icon.static.get("error")} | Esse pet não foi encontrado para adoção"
                )
                return
            }

            if (adoptionPet.get(USERPET.USERID) == event.user.id) {
                withContext(Dispatchers.IO) {
                    dsl.deleteFrom(ADOPTIONCENTER)
                        .where(ADOPTIONCENTER.ID.eq(adoptionPet.get(ADOPTIONCENTER.ID)))
                        .execute()
                }
                event.rapidContainerReply(
                    Colors.SUCCESS,
                    "${Icon.static.get("Eris_happy")} | Que bom que você decidiu pegar seu pet de volta! ele agora faz parte de sua família novamente. (por favor não o abandone novamente)"
                )
                return
            }

            withContext(Dispatchers.IO) {
                dsl.transaction { config ->
                    val tx = config.dsl()

                    tx.deleteFrom(ADOPTIONCENTER)
                        .where(ADOPTIONCENTER.ID.eq(adoptionPet.get(ADOPTIONCENTER.ID)))
                        .execute()
                    tx.getOrCreateUser(event.user.id)
                    tx.update(USERPET)
                        .set(USERPET.USERID, event.user.id)
                        .where(USERPET.ID.eq(adoptionPet.get(USERPET.ID)))
                        .execute()
                }
            }

            event.rapidContainerReply(
                Colors.SUCCESS,
                "${Icon.static.get("Eris_happy")} | Você adotou o pet **${adoptionPet.get(USERPET.NAME)}** com sucesso! agora ele faz parte de sua família."
            )
        }
    }
}