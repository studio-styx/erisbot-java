package studio.styx.erisbot.discord.features.commands.pets

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.Subcommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.discord.features.commands.pets.subCommands.PetAdoptionCenterCommand
import studio.styx.erisbot.discord.features.commands.pets.subCommands.PetBreedCommand
import studio.styx.erisbot.discord.features.commands.pets.subCommands.PetChangeName
import studio.styx.erisbot.discord.features.commands.pets.subCommands.PetInfoCommand
import studio.styx.erisbot.discord.features.commands.pets.subCommands.PetReleaseCommand
import studio.styx.erisbot.discord.features.commands.pets.subCommands.PetSetActiveCommand
import studio.styx.erisbot.discord.features.commands.pets.subCommands.PetSpinCommand
import studio.styx.erisbot.generated.tables.references.PET
import studio.styx.erisbot.generated.tables.references.USERPET
import java.util.concurrent.TimeUnit

@Component
class PetsCommands : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override fun getSlashCommandData(): SlashCommandData {
        return Command("pets", "Comandos de pets")
            .addSubcommands(
                Subcommand("info", "Obter informações de um pet especifico")
                    .addOption(OptionType.STRING, "pet", "pet para cuidar", true, true),
                Subcommand("spin", "girar a roleta de pets"),
                Subcommand("care", "cuidar de um pet")
                    .addOption(OptionType.STRING, "pet", "pet para cuidar", true, true),
                Subcommand("breed", "reproduzir seus pets")
                    .addOption(OptionType.STRING, "pet1", "pet para reproduzir", true, true)
                    .addOption(OptionType.STRING, "pet2", "pet com quem ele irá reproduzir", true, true),
                Subcommand("adopt", "adotar um pet"),
                Subcommand("release", "enviar um pet seu para a adoção")
                    .addOption(OptionType.STRING, "pet", "pet para enviar para adoção", true, true),
                Subcommand("set_active", "definir um pet como ativo")
                    .addOption(OptionType.STRING, "pet", "pet para definir como ativo", true, true),
                Subcommand("change_name", "mudar o nome de um pet")
                    .addOption(OptionType.STRING, "pet", "pet para trocar o nome", true, true)
            )
    }

    override suspend fun onAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        val focused = event.focusedOption
        val subCommand = event.subcommandName

        when (focused.name) {
            "pet" -> {
                val pets = getUserPetsSimple(event.user.id)

                val petsFiltered = pets.filter {
                    it.key.contains(focused.value, true)
                }.map {
                    Command.Choice(it.key, it.value.toString())
                }.take(25)

                if (petsFiltered.isEmpty()) {
                    event.replyChoice("Nenhum pet encontrado", "null").await()
                    return
                }

                event.replyChoices(petsFiltered).await()
            }
            "pet1", "pet2" -> {
                if (subCommand != "breed") return

                val pets = getUserPetsFull(event.user.id)

                if (pets.isEmpty()) {
                    event.replyChoice("Nenhum pet encontrado", "null").await()
                    return
                }

                if (focused.name == "pet1") {
                    val filteredPets = pets.filter {
                        it.get(USERPET.ISPREGNANT) == false && it.get(USERPET.NAME)!!.contains(focused.value, true)
                    }

                    val choices = filteredPets.map {
                        Command.Choice(it.get(USERPET.NAME)!!, it.get(USERPET.ID).toString())
                    }.take(25)

                    if (choices.isEmpty()) {
                        event.replyChoice("Nenhum pet encontrado", "null").await()
                        return
                    }

                    event.replyChoices(choices).await()
                } else {
                    val pet1 = event.getOption("pet1")?.asString ?: run {
                        event.replyChoice("Por favor insira o primeiro pet primeiro", "null").await()
                        return
                    }

                    val pet1Id = pet1.toIntOrNull() ?: run {
                        event.replyChoice("Por favor insira um pet válido", "null").await()
                        return
                    }

                    val pet1Data = pets.find { it.get(USERPET.ID) == pet1Id } ?: run {
                        event.replyChoice("Por favor insira um pet válido", "null").await()
                        return
                    }

                    val filteredPets = pets.filter {
                        it.get(USERPET.ID) == pet1Id && it.get(USERPET.ISPREGNANT) == false &&
                                it.get(USERPET.NAME)!!.contains(focused.value, true) &&
                                it.get(USERPET.GENDER) != pet1Data.get(USERPET.GENDER) &&
                                it.get(PET.ANIMAL) == pet1Data.get(PET.ANIMAL)
                    }

                    val choices = filteredPets.map {
                        Command.Choice(it.get(USERPET.NAME)!!, it.get(USERPET.ID).toString())
                    }.take(25)

                    if (choices.isEmpty()) {
                        event.replyChoice("Nenhum pet válido foi encontrado para acasalar com ${pet1Data.get(USERPET.NAME)}", "null").await()
                        return
                    }

                    event.replyChoices(choices).await()
                }
            }
        }
    }

    private suspend fun getUserPetsFull(userId: String): List<Record> {
        val key = "userPets:full:$userId"
        val cached = Cache.get<List<Record>>(key)
        if (cached != null) return cached

        val pets = withContext(Dispatchers.IO) {
            dsl.select(USERPET.asterisk(), PET.asterisk())
                .from(USERPET)
                .innerJoin(PET).on(USERPET.PETID.eq(PET.ID))
                .where(USERPET.USERID.eq(userId))
                .and(USERPET.ISDEAD.eq(false))
                .and(USERPET.adoptioncenter.eq(null))
                .fetch()
        }

        Cache.set(key, pets, 2, TimeUnit.MINUTES)

        return pets
    }

    private suspend fun getUserPetsSimple(userId: String): MutableMap<String, Int> {
        val key = "userPets:simple:$userId"
        val cached = Cache.get<MutableMap<String, Int>>(key)
        if (cached != null) return cached
        val pets = withContext(Dispatchers.IO) {
            dsl.select(USERPET.NAME, USERPET.ID)
                .from(USERPET)
                .where(USERPET.USERID.eq(userId))
                .and(USERPET.ISDEAD.eq(false))
                .and(USERPET.adoptioncenter.eq(null))
                .fetchMap(USERPET.NAME, USERPET.ID)
        }.mapNotNull {
            it.key!! to it.value!!
        }.toMap().toMutableMap()

        Cache.set(key, pets, 2, TimeUnit.MINUTES)

        return pets
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        val subCommand = event.subcommandName!!

        when (subCommand) {
            "spin" -> PetSpinCommand(event, dsl).execute()
            "info" -> PetInfoCommand(event, dsl).execute()
            "release" -> PetReleaseCommand(event, dsl).execute()
            "set_active" -> PetSetActiveCommand(event, dsl).execute()
            "change_name" -> PetChangeName(event, dsl).execute()
            "breed" -> PetBreedCommand(event, dsl).execute()
            "adopt" -> PetAdoptionCenterCommand(event, dsl).execute()
        }
    }
}