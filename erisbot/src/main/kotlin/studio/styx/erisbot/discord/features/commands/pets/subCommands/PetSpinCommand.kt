package studio.styx.erisbot.discord.features.commands.pets.subCommands

import database.extensions.getOrCreateUser
import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.reply.rapidContainerReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.rand
import shared.Colors
import shared.utils.Icon
import shared.utils.Utils
import studio.styx.erisbot.generated.enums.Gender
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.PersonalitytraitRecord
import studio.styx.erisbot.generated.tables.references.*
import utils.ComponentBuilder
import java.time.LocalDateTime

class PetSpinCommand(
    private val event: SlashCommandInteractionEvent,
    private val dsl: DSLContext
) {
    // Pesos e Nomes (Estáticos)
    private val rarityWeights: Map<Rarity, Double> = mapOf(
        Rarity.LEGENDARY to 2.0,
        Rarity.EPIC to 12.0,
        Rarity.RARE to 30.0,
        Rarity.UNCOMUM to 40.0,
        Rarity.COMUM to 60.0
    )

    private val randomNames: Map<Gender, List<String>> = mapOf(
        Gender.MALE to listOf(
            "Rex", "Bolt", "Max", "Thor", "Simba", "Leo", "Rocky", "Spike", "Odin", "Zeus",
            "Milo", "Apollo", "Charlie", "Finn", "Hunter", "Shadow", "Toby", "Rusty", "Buster", "Ace",
            "Duke", "Sammy", "Tiger", "Jack", "Lucky", "Bear", "Scout", "King", "Gizmo", "Cosmo",
            "Ranger", "Blaze", "Samson", "Jasper", "Chico", "Bandit", "Oscar", "Hercules", "Finnick", "Arlo"
        ),
        Gender.FEMALE to listOf(
            "Luna", "Bella", "Mia", "Nala", "Athena", "Daisy", "Cleo", "Ruby", "Sophie", "Chloe",
            "Lily", "Zoe", "Molly", "Rosie", "Willow", "Harper", "Stella", "Ivy", "Ella", "Jasmine",
            "Sadie", "Penny", "Lucy", "Maya", "Roxy", "Nina", "Aurora", "Ginger", "Hazel", "Olivia",
            "Fiona", "Flora", "Maisie", "Trixie", "Violet", "Mimi", "Coco", "Pepper", "Lacey", "Dottie"
        )
    )

    private fun getRandomRarity(): Rarity {
        val totalWeight = rarityWeights.values.sum()
        val random = Math.random() * totalWeight
        var cumulative = 0.0

        for ((rarity, weight) in rarityWeights) {
            cumulative += weight
            if (random <= cumulative) return rarity
        }
        return Rarity.COMUM
    }

    // Função de busca de pet (Sem Halloween)
    private suspend fun getRandomPet(rarity: Rarity): studio.styx.erisbot.generated.tables.records.PetRecord? {
        return withContext(Dispatchers.IO) {
            dsl.selectFrom(PET)
                .where(PET.RARITY.eq(rarity))
                .and(PET.ISENABLED.eq(true))
                .orderBy(rand()) // Random do SQL
                .limit(1)
                .fetchOne()
        }
    }

    // Classe de dados para transportar o resultado da criação sem precisar de um novo SELECT complexo
    data class PetCreationResult(
        val userPetId: Long,
        val traitNames: List<String>,
        val skillInfos: List<String>,
        val geneticInfos: List<String>
    )

    private suspend fun createUserPet(
        userId: String,
        petId: Int,
        gender: Gender,
        name: String
    ): PetCreationResult = withContext(Dispatchers.IO) {
        dsl.transactionResult { config ->
            val ctx = DSL.using(config)

            // 1. Buscando catálogos
            val geneticsCatalog = ctx.selectFrom(GENETICS).where(GENETICS.PETID.eq(petId)).fetch()
            val possibleSkills = ctx.selectFrom(PETSKILL).fetch()
            val possibleTraits = ctx.selectFrom(PERSONALITYTRAIT).fetch()

            // 2. Genética
            val groupedGenetics = geneticsCatalog.groupBy { it.colorpart }
            val selectedGenes = mutableListOf<studio.styx.erisbot.generated.tables.records.GeneticsRecord>()

            groupedGenetics.forEach { (_, candidates) ->
                val weights = candidates.map { gene ->
                    when (gene.genetype.toString()) {
                        "DOMINANT" -> 50
                        "CODOMINANT" -> 30
                        "NEUTRAL" -> 15
                        "RECESSIVE" -> 5
                        else -> 10
                    }
                }

                val totalWeight = weights.sum()
                val random = Math.random() * totalWeight
                var cumulative = 0

                for (i in candidates.indices) {
                    cumulative += weights[i]
                    if (random <= cumulative) {
                        selectedGenes.add(candidates[i])
                        break
                    }
                }
            }

            // 3. Skills (40%)
            val selectedSkill = if (Math.random() <= 0.4 && possibleSkills.isNotEmpty()) {
                possibleSkills.random()
            } else null

            // 4. Personalidades
            val shuffledTraits = possibleTraits.shuffled()
            val selectedTraits = mutableListOf<PersonalitytraitRecord>()
            var remainingSlots = if (Math.random() < 0.3) 2 else 1

            for (trait in shuffledTraits) {
                if (remainingSlots == 0) break
                val conflicts = trait.personalityconflictnames ?: emptyArray()
                val hasConflict = selectedTraits.any { selected ->
                    conflicts.contains(selected.name) ||
                            (selected.personalityconflictnames ?: emptyArray()).contains(trait.name)
                }
                if (!hasConflict) {
                    selectedTraits.add(trait)
                    remainingSlots--
                }
            }

            // 5. Inserções

            ctx.getOrCreateUser(userId)
            val userPetRecord = ctx.insertInto(USERPET)
                .set(USERPET.USERID, userId)
                .set(USERPET.PETID, petId)
                .set(USERPET.GENDER, gender)
                .set(USERPET.NAME, name)
                .set(USERPET.HUMOR, "happy")
                .returning(USERPET.ID)
                .fetchOne()!!

            selectedGenes.forEach { gene ->
                ctx.insertInto(PETGENETICS)
                    .set(PETGENETICS.USERPETID, userPetRecord.id)
                    .set(PETGENETICS.GENEID, gene.id)
                    .set(PETGENETICS.INHERITEDFROMPARENT1, false)
                    .set(PETGENETICS.INHERITEDFROMPARENT2, false)
                    .execute()
            }

            if (selectedSkill != null) {
                ctx.insertInto(USERPETSKILL)
                    .set(USERPETSKILL.USERPETID, userPetRecord.id)
                    .set(USERPETSKILL.SKILLID, selectedSkill.id)
                    .set(USERPETSKILL.LEVEL, 1)
                    .execute()
            }

            selectedTraits.forEach { trait ->
                ctx.insertInto(USERPETPERSONALITY)
                    .set(USERPETPERSONALITY.USERPETID, userPetRecord.id)
                    .set(USERPETPERSONALITY.TRAITID, trait.id)
                    .execute()
            }

            // Retornando DTO com dados formatados para evitar queries extras
            PetCreationResult(
                userPetId = userPetRecord.id!!.toLong(),
                traitNames = selectedTraits.mapNotNull { it.name },
                skillInfos = if (selectedSkill != null) listOf("**`${selectedSkill.name}`** - Nível **1**") else emptyList(),
                geneticInfos = selectedGenes.map { "**`${it.trait}` - `(${it.colorpart})`** [**${it.genetype}**]" }
            )
        }
    }

    suspend fun execute() {
        // Defer inicial
        if (!event.interaction.isAcknowledged) event.deferReply().await()

        val userId = event.user.id

        // --- Check Cooldown ---
        val cooldown = withContext(Dispatchers.IO) {
            dsl.selectFrom(COOLDOWN)
                .where(COOLDOWN.USERID.eq(userId))
                .and(COOLDOWN.NAME.eq("petSpin"))
                .fetchOne()
        }

        if (cooldown != null && cooldown.willendin!!.isAfter(LocalDateTime.now())) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("denied")} | Você está em cooldown! Você pode girar pets novamente ${Utils.formatDiscordTime(cooldown.willendin!!, "R")}"
            )
            return
        }

        event.rapidContainerReply(
            Colors.DANGER,
            "${Icon.static.get("waiting_white")} | Girando a roleta..."
        )

        // --- Lógica Principal ---
        val rarity = getRandomRarity()
        val pet = getRandomPet(rarity)

        if (pet == null) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("error")} | Não foi possível encontrar um pet adequado! Desculpe-me, isso é um erro meu! ${Icon.static.get("Eris_cry")}"
            )
            return
        }

        val petGender = if (Utils.calculateProbability(50)) Gender.MALE else Gender.FEMALE
        val petName = randomNames[petGender]!!.random()

        // Transação
        val result = createUserPet(userId, pet.id!!, petGender, petName)

        // --- Log & Cooldown Update ---
        val petRarityLogLevel = mapOf(
            Rarity.LEGENDARY to 10, Rarity.EPIC to 8, Rarity.RARE to 6, Rarity.UNCOMUM to 4, Rarity.COMUM to 2
        )

        val cooldownEnd = LocalDateTime.now().plusHours(2)

        withContext(Dispatchers.IO) {
            dsl.insertInto(LOG)
                .set(LOG.USERID, userId)
                .set(LOG.TYPE, "info")
                .set(LOG.MESSAGE, "Ganhou um pet ${pet.rarity} (${pet.animal}) ao girar a roleta de pets.")
                .set(LOG.TAGS, arrayOf("pet", "spin", "reward", "PETID_${pet.id}", "USERPETID_${result.userPetId}", "RARITY_${pet.rarity}"))
                .set(LOG.LEVEL, petRarityLogLevel[pet.rarity] ?: 2)
                .execute()

            dsl.insertInto(COOLDOWN)
                .set(COOLDOWN.USERID, userId)
                .set(COOLDOWN.NAME, "petSpin")
                .set(COOLDOWN.WILLENDIN, cooldownEnd)
                .onDuplicateKeyUpdate()
                .set(COOLDOWN.WILLENDIN, cooldownEnd)
                .execute()
        }

        // --- Construção da Resposta (ContainerBuilder) ---
        val traitsStr = if (result.traitNames.isNotEmpty()) result.traitNames.joinToString(", ") else "Nenhuma"
        val skillsStr = if (result.skillInfos.isNotEmpty()) result.skillInfos.joinToString(", ") else "Nenhuma"
        val geneticsStr = if (result.geneticInfos.isNotEmpty()) result.geneticInfos.joinToString(", ") else "Nenhuma"

        val container = ComponentBuilder.ContainerBuilder.create()
            .withColor(Colors.SUCCESS)
            .addText("## Giro de Pet")
            .addDivider()
            .addText("Você deu um giro e conseguiu um pet **${pet.rarity.toString().lowercase()}**!")
            .addText(Utils.brBuilder(
                "### Detalhes do Pet",
                "**Nome:** $petName",
                "**Animal:** ${pet.animal} (Espécie: **${pet.specie}**)",
                "**Raridade:** ${pet.rarity}",
                "**Gênero:** ${if (petGender == Gender.MALE) "Macho" else "Fêmea"}",
                "**Personalidades:** $traitsStr",
                "**Humor:** feliz",
                "**Habilidades:** $skillsStr",
                "**Genética:** $geneticsStr"
            ))
            .addDivider()
            .addText(Utils.brBuilder(
                "Você pode renomear seu pet ou colocá-lo para adoção.",
                "Cooldown até: ${Utils.formatDiscordTime(cooldownEnd, "R")}"
            ))

        // Botões
        val components = ActionRow.of(
            Button.primary("pet/spin/name/$userId/${result.userPetId}", "Trocar Nome"),
            Button.danger("pet/spin/del/$userId/${result.userPetId}", "Desfazer Pet")
        )

        // Resposta final
        event.hook.sendMessageComponents(container.build(), components).useComponentsV2().queue()
    }
}