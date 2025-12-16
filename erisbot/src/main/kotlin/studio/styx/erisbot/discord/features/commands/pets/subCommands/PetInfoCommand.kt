package studio.styx.erisbot.discord.features.commands.pets.subCommands

import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.reply.rapidContainerReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import org.jooq.Record
import shared.Colors
import shared.utils.Icon
import shared.utils.Utils
import studio.styx.erisbot.generated.enums.Gender
import studio.styx.erisbot.generated.tables.records.PetRecord
import studio.styx.erisbot.generated.tables.records.UserpetRecord
import studio.styx.erisbot.generated.tables.references.*
import utils.ComponentBuilder

class PetInfoCommand(
    private val event: SlashCommandInteractionEvent,
    private val dsl: DSLContext
) {
    // DTO para transportar os dados
    private data class PetInfoResult(
        val userPet: UserpetRecord,
        val pet: PetRecord,
        val skills: List<Record>, // Join UserPetSkill + PetSkill
        val genetics: List<Record>, // Join UserPetGenetics + Genetics
        val personalities: List<Record>, // Join UserPetPersonality + PersonalityTrait
        val parent1: UserpetRecord?,
        val parent2: UserpetRecord?,
        val children: List<UserpetRecord>,
    )

    private suspend fun getPetInfo(petId: Int, userId: String): PetInfoResult? = coroutineScope {
        // 1. Busca Principal (UserPet)
        val userPet = withContext(Dispatchers.IO) {
            dsl.selectFrom(USERPET)
                .where(USERPET.ID.eq(petId))
                .and(USERPET.USERID.eq(userId)) // Garante que o pet é do usuário (como no TS "getValidUserPet")
                .and(USERPET.ISDEAD.eq(false)) // Opcional, dependendo da regra
                .fetchOne()
        } ?: return@coroutineScope null

        // 2. Buscas Paralelas (Já que temos o userPet)
        val petDefDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(PET).where(PET.ID.eq(userPet.petid)).fetchOne()
        }

        val skillsDeferred = async(Dispatchers.IO) {
            dsl.select(USERPETSKILL.LEVEL, PETSKILL.NAME)
                .from(USERPETSKILL)
                .join(PETSKILL).on(USERPETSKILL.SKILLID.eq(PETSKILL.ID))
                .where(USERPETSKILL.USERPETID.eq(petId))
                .fetch()
        }

        val geneticsDeferred = async(Dispatchers.IO) {
            // Precisamos dos campos de herança da tabela de ligação e dos dados do gene
            dsl.select(
                GENETICS.TRAIT,
                GENETICS.COLORPART,
                GENETICS.GENETYPE,
                PETGENETICS.INHERITEDFROMPARENT1,
                PETGENETICS.INHERITEDFROMPARENT2
            )
                .from(PETGENETICS)
                .join(GENETICS).on(PETGENETICS.GENEID.eq(GENETICS.ID))
                .where(PETGENETICS.USERPETID.eq(petId))
                .fetch()
        }

        val personalitiesDeferred = async(Dispatchers.IO) {
            dsl.select(PERSONALITYTRAIT.NAME)
                .from(USERPETPERSONALITY)
                .join(PERSONALITYTRAIT).on(USERPETPERSONALITY.TRAITID.eq(PERSONALITYTRAIT.ID))
                .where(USERPETPERSONALITY.USERPETID.eq(petId))
                .fetch()
        }

        val parentsDeferred = async(Dispatchers.IO) {
            val p1 = if (userPet.parent1id != null)
                dsl.selectFrom(USERPET).where(USERPET.ID.eq(userPet.parent1id)).fetchOne() else null
            val p2 = if (userPet.parent2id != null)
                dsl.selectFrom(USERPET).where(USERPET.ID.eq(userPet.parent2id)).fetchOne() else null
            Pair(p1, p2)
        }

        val childrenDeferred = async(Dispatchers.IO) {
            dsl.selectFrom(USERPET)
                .where(USERPET.PARENT1ID.eq(petId))
                .or(USERPET.PARENT2ID.eq(petId))
                .fetch()
        }

        val petDef = petDefDeferred.await()!! // Se tem UserPet, tem Pet
        val skills = skillsDeferred.await()
        val genetics = geneticsDeferred.await()
        val personalities = personalitiesDeferred.await()
        val (p1, p2) = parentsDeferred.await()
        val children = childrenDeferred.await()

        PetInfoResult(
            userPet = userPet,
            pet = petDef,
            skills = skills,
            genetics = genetics,
            personalities = personalities,
            parent1 = p1,
            parent2 = p2,
            children = children,
        )
    }

    suspend fun execute() {
        if (!event.interaction.isAcknowledged) event.deferReply().await()

        val petIdInput = event.getOption("pet")?.asString
        val petId = petIdInput?.toIntOrNull()

        if (petId == null) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Por favor forneça um id de pet válido!")
            return
        }

        val data = getPetInfo(petId, event.user.id) // Passando ID do user para segurança
        if (data == null) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Não consegui encontrar esse pet ou ele não pertence a você!")
            return
        }

        // --- Formatação dos Textos ---

        // 1. Habilidades
        val skillsStr = if (data.skills.isNotEmpty()) {
            data.skills.joinToString(", ") { record ->
                "**`${record[PETSKILL.NAME]}`** - Nível **${record[USERPETSKILL.LEVEL]}**"
            }
        } else "Nenhuma"

        // 2. Genética (Lógica complexa do TS portada)
        val geneticsStr = if (data.genetics.isNotEmpty()) {
            data.genetics.joinToString("\n") { record ->
                val trait = record[GENETICS.TRAIT]
                val colorPart = record[GENETICS.COLORPART]
                val typeRaw = record[GENETICS.GENETYPE].toString() // Enum to String

                val fromP1 = record[PETGENETICS.INHERITEDFROMPARENT1] ?: false
                val fromP2 = record[PETGENETICS.INHERITEDFROMPARENT2] ?: false

                val origin = when {
                    fromP1 && fromP2 -> "Ambos"
                    fromP1 -> "Pai"
                    fromP2 -> "Mãe"
                    else -> "Espécie"
                }

                val domType = when (typeRaw) {
                    "DOMINANT" -> "`Dominante`"
                    "CODOMINANT" -> "`Codominante`"
                    else -> "`Recessivo`"
                }

                "> **`$trait`** ($colorPart) [$domType, herdado de **$origin**]"
            }
        } else "Nenhuma informação genética"

        // 3. Pais
        val parentsStr = if (data.parent1 != null || data.parent2 != null) {
            val p1Name = data.parent1?.name?.let { "**`$it`**" } ?: "Desconhecido"
            val p2Name = data.parent2?.name?.let { "**`$it`**" } ?: "Desconhecido"
            "$p1Name x $p2Name"
        } else "Nenhum (geração inicial)"

        // 4. Filhos
        val childrenStr = if (data.children.isNotEmpty()) {
            data.children.joinToString(", ") { "**`${it.name}`**" }
        } else "Nenhum"

        // 5. Gravidez
        val pregnancyStr = if (data.userPet.gender == Gender.FEMALE) {
            // Assumindo que ispregnant existe no record
            val isPregnant = data.userPet.ispregnant ?: false
            if (isPregnant && data.userPet.pregnantendat != null) {
                "\n**Está grávida?**: Sim, termina ${Utils.formatDiscordTime(data.userPet.pregnantendat!!, "R")}"
            } else {
                "\n**Está grávida?**: Não"
            }
        } else ""

        // Construção da Lista de Linhas
        val lines = buildList {
            add("**Nome: `${data.userPet.name}`**")
            add("**Animal: `${data.pet.animal}`** (Espécie: **`${data.pet.specie}`**)") // Assumindo enum formatado ou string
            add("**Raridade: `${data.pet.rarity}`**")
            add("**Gênero: `${if (data.userPet.gender == Gender.MALE) "Macho" else "Fêmea"}`**")
            add("**Humor: `${data.userPet.humor}`**")

            // Estatísticas
            add("**Estatísticas:**")
            add("> **Fome:** ${data.userPet.hungry}/100")
            add("> **Felicidade:** ${data.userPet.happiness}/100")
            add("> **Energia:** ${data.userPet.energy}/100")
            add("> **Vida:** ${data.userPet.life}/100")

            val persStr = data.personalities.joinToString(", ") { "**`${it[PERSONALITYTRAIT.NAME]}`**" }
            add("**Personalidades:** $persStr")

            add("**Habilidades:** $skillsStr")

            add("**Genética:**")
            add(geneticsStr)

            add("**Pais:** $parentsStr")
            add("**Filhos:** $childrenStr")

            if (pregnancyStr.isNotEmpty()) add(pregnancyStr)
        }

        // --- Envio ---
        ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.FUCHSIA)
            .disableMentions()
            .addText("## Informações do Pet")
            .addDivider()
            .addText(Utils.brBuilder(*lines.toTypedArray()))
            .reply(event)
    }
}