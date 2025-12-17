package studio.styx.erisbot.discord.features.commands.pets.subCommands

import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.reply.rapidContainerReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import org.jooq.impl.DSL
import shared.Colors
import shared.utils.DiscordTimeStyle
import shared.utils.Icon
import shared.utils.Utils
import studio.styx.erisbot.generated.enums.Animal
import studio.styx.erisbot.generated.enums.Gender
import studio.styx.erisbot.generated.tables.references.LOG
import studio.styx.erisbot.generated.tables.references.PET
import studio.styx.erisbot.generated.tables.references.USERPET
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import studio.styx.schemaEXtended.core.schemas.ObjectSchema
import java.time.LocalDateTime

class PetBreedCommand(
    private val event: SlashCommandInteractionEvent,
    private val dsl: DSLContext
) {
    // Mapa de duração em minutos baseado no Enum Animal
    private val PREGNANCY_DURATION_MINUTES = mapOf(
        Animal.CAT to 60L * 12,
        Animal.DOG to 60L * 12,
        Animal.BIRD to 60L * 8,
        Animal.DRAGON to 60L * 24 * 12,
        Animal.HAMSTER to 60L * 4,
        Animal.JAGUAR to 60L * 24 * 5,
        Animal.LION to 60L * 24 * 7,
        Animal.RABBIT to 60L * 10,
        Animal.BAT to 60L * 6,
        Animal.BLACK_CAT to 60L * 12,
        Animal.GHOST_DOG to 60L * 12,
        Animal.PUMPKIN_GOLEM to 60L * 24 * 10,
        Animal.RAVEN to 60L * 8,
        Animal.WOLF to 60L * 24 * 6,
        Animal.SKELETON_HORSE to 60L * 24 * 8,
        Animal.SPIDER to 60L * 5,
        Animal.ZOMBIE_RABBIT to 60L * 10
    )

    private val SCHEMA = ObjectSchema()
        .addProperty("pet1", NumberSchema().parseError("O id do pet 1 deve ser um inteiro válido").min(1).minError("O id do pet 1 deve ser maior que 0").coerce())
        .addProperty("pet2", NumberSchema().parseError("O id do pet 2 deve ser um inteiro válido").min(1).minError("O id do pet 2 deve ser maior que 0").coerce())

    suspend fun execute() {
        val schemaResult = SCHEMA.parseOrThrow(
            mapOf(
                "pet1" to event.getOption("pet1")?.asString,
                "pet2" to event.getOption("pet2")?.asString
            )
        )

        val pet1Id = schemaResult.getInteger("pet1")
        val pet2Id = schemaResult.getInteger("pet2")

        event.deferReply().await()

        // Carregar dados e contagem de filhos
        val (pet1, pet2, pet1ChildCount, pet2ChildCount) = withContext(Dispatchers.IO) {
            val petsMap = dsl.select(USERPET.asterisk(), PET.asterisk())
                .from(USERPET)
                .innerJoin(PET).on(USERPET.PETID.eq(PET.ID))
                .where(USERPET.ID.`in`(pet1Id, pet2Id))
                .and(USERPET.USERID.eq(event.user.id))
                .and(USERPET.ISDEAD.eq(false))
                .and(USERPET.adoptioncenter.eq(null))
                .fetch()
                .associateBy { it.get(USERPET.ID) }

            // Verifica quantas vezes o ID aparece como PARENT1 ou PARENT2
            val childCounts = dsl.select(
                USERPET.PARENT1ID,
                USERPET.PARENT2ID,
                DSL.count()
            )
                .from(USERPET)
                .where(USERPET.PARENT1ID.`in`(pet1Id, pet2Id).or(USERPET.PARENT2ID.`in`(pet1Id, pet2Id)))
                .groupBy(USERPET.PARENT1ID, USERPET.PARENT2ID)
                .fetch()

            // Calcula totais baseados no resultado
            fun countChildren(id: Int): Int {
                return childCounts.filter {
                    it[USERPET.PARENT1ID] == id || it[USERPET.PARENT2ID] == id
                }.sumOf { it.value3() } // value3 é o count
            }

            val p1 = petsMap[pet1Id]
            val p2 = petsMap[pet2Id]

            // Retorna Tupla com Pets e Contagens
            Tuples4(p1, p2, if (p1 != null) countChildren(pet1Id) else 0, if (p2 != null) countChildren(pet2Id) else 0)
        }

        // 1. Verificar Existência
        if (pet1 == null || pet2 == null) {
            val message = when {
                pet1 == null && pet2 == null -> "Eu não consegui encontrar nenhum desses dois pets!"
                pet1 == null -> "Eu não consegui encontrar o primeiro pet!"
                else -> "Eu não consegui encontrar o segundo pet!"
            }
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | $message")
            return
        }

        // 2. Verificar se é o mesmo pet
        if (pet1Id == pet2Id) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Um pet não pode se reproduzir com ele mesmo!")
            return
        }

        // 3. Verificar compatibilidade de Espécie
        if (pet1.get(PET.ANIMAL) != pet2.get(PET.ANIMAL)) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Os pets são animais diferentes!")
            return
        }

        // 4. Verificar Sexo
        val gender1 = pet1.get(USERPET.GENDER)
        val gender2 = pet2.get(USERPET.GENDER)

        if (gender1 == gender2) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Eu sei que esse é um assunto polêmico, mas pets do mesmo sexo não podem acasalar!")
            return
        }

        // 5. Verificar Gravidez
        if (pet1.get(USERPET.ISPREGNANT) == true || pet2.get(USERPET.ISPREGNANT) == true) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | A fêmea já está grávida! Espere ela parir para poder reproduzir novamente!")
            return
        }

        // --- VERIFICAÇÕES DE PARENTESCO ---

        val p1Parents = listOfNotNull(pet1.get(USERPET.PARENT1ID), pet1.get(USERPET.PARENT2ID))
        val p2Parents = listOfNotNull(pet2.get(USERPET.PARENT1ID), pet2.get(USERPET.PARENT2ID))

        // 6. Pai/Filho Direto
        // Se o ID do pet1 está na lista de pais do pet2 OU vice-versa
        val isDirectParentChild = pet1Id in p2Parents || pet2Id in p1Parents

        if (isDirectParentChild) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Você não pode acasalar pais e filhos!")
            return
        }

        // 7. Irmãos (Mesmos pais)
        // Se houver qualquer interseção entre as listas de pais (excluindo nulos, já tratado no listOfNotNull)
        val areSiblings = p1Parents.any { it in p2Parents }

        if (areSiblings) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Você não pode acasalar irmãos!")
            return
        }

        // 8. Verificar Casamento (Spouse)
        if (pet1.get(USERPET.SPOUSEID) != null || pet2.get(USERPET.SPOUSEID) != null) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Um dos pets já está em um relacionamento!")
            return
        }

        // 9. Verificar Limite de Filhos (Max 5)
        if (pet1ChildCount >= 5 || pet2ChildCount >= 5) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Um pet não pode ter mais que 5 filhos!")
            return
        }

        // --- LÓGICA DE REPRODUÇÃO ---

        val female = if (gender1 == Gender.FEMALE) pet1 else pet2
        val male = if (gender1 == Gender.MALE) pet1 else pet2

        val durationMinutes = PREGNANCY_DURATION_MINUTES[female.get(PET.ANIMAL)] ?: (60L * 12) // Default fallback
        val pregnantEndDate = LocalDateTime.now().plusMinutes(durationMinutes)

        // Transação no Banco de Dados
        withContext(Dispatchers.IO) {
            dsl.transaction { config ->
                val ctx = DSL.using(config)

                // Atualizar Fêmea
                ctx.update(USERPET)
                    .set(USERPET.ISPREGNANT, true)
                    .set(USERPET.PREGNANTENDAT, pregnantEndDate)
                    .set(USERPET.SPOUSEID, male.get(USERPET.ID))
                    .where(USERPET.ID.eq(female.get(USERPET.ID)))
                    .execute()

                // Atualizar Macho
                ctx.update(USERPET)
                    .set(USERPET.SPOUSEID, female.get(USERPET.ID))
                    .where(USERPET.ID.eq(male.get(USERPET.ID)))
                    .execute()

                // Criar Log
                ctx.insertInto(LOG)
                    .set(LOG.USERID, event.user.id)
                    .set(LOG.MESSAGE, "Acasalou seu pet **${male.get(USERPET.NAME)}** com **${female.get(USERPET.NAME)}**")
                    .set(LOG.LEVEL, 6)
                    .set(LOG.TAGS, arrayOf("pet", "breed", "reproduction", "reproduction pet", male.get(USERPET.ID).toString(), female.get(USERPET.ID).toString()))
                    .execute()
            }
        }

        // Timestamp do Discord: <t:EPOCH:R> (Relative)
        val discordTime = Utils.formatDiscordTime(pregnantEndDate, DiscordTimeStyle.RELATIVE)

        event.rapidContainerReply(
            Colors.SUCCESS,
            "${Icon.static.get("success")} | Você colocou seu pet **${male.get(USERPET.NAME)}** para se reproduzir com **${female.get(USERPET.NAME)}**! Ela está grávida e irá parir em $discordTime"
        )
    }

    // Helper data class para o retorno do withContext
    data class Tuples4<A, B, C, D>(val a: A?, val b: B?, val c: C, val d: D)
}