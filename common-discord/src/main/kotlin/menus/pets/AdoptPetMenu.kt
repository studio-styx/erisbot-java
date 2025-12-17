package menus.pets

import net.dv8tion.jda.api.components.MessageTopLevelComponent
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import org.jooq.Record
import shared.utils.Utils
import studio.styx.erisbot.generated.enums.Animal
import studio.styx.erisbot.generated.enums.Gender
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.AdoptioncenterRecord
import studio.styx.erisbot.generated.tables.records.PetRecord
import studio.styx.erisbot.generated.tables.records.UserpetRecord
import studio.styx.erisbot.generated.tables.references.PERSONALITYTRAIT
import utils.ComponentBuilder

data class AdoptionCenterMenuData(
    val userPet: UserpetRecord,
    val pet: PetRecord,
    val personality: List<Record>,
    val skills: List<Record>,
    val adoptionCenter: AdoptioncenterRecord
)

private val petAnimalFormatted = mapOf<Animal, String>(
    Animal.CAT to "gato",
    Animal.DOG to "cachorro",
    Animal.BIRD to "pássaro",
    Animal.DRAGON to "dragão",
    Animal.HAMSTER to "hamster",
    Animal.JAGUAR to "jaguar",
    Animal.LION to "leão",
    Animal.RABBIT to "coelho",
    Animal.BAT to "morcego",
    Animal.BLACK_CAT to "gato preto",
    Animal.GHOST_DOG to "cachorro fantasma",
    Animal.PUMPKIN_GOLEM to "golem de abóbora",
    Animal.RAVEN to "gavião",
    Animal.WOLF to "lobo",
)

private val petRarityFormatted = mapOf<Rarity, String>(
    Rarity.COMUM to "comum",
    Rarity.UNCOMUM to "incomum",
    Rarity.RARE to "raro",
    Rarity.EPIC to "épico",
    Rarity.LEGENDARY to "lendário",
)

fun adoptPetMenu(pets: List<AdoptionCenterMenuData>, page: Int = 0): MutableList<MessageTopLevelComponent> {
    val petsPerPage = 6
    val startIndex = page * petsPerPage
    val endIndex = startIndex + petsPerPage
    val petsInPage = pets.slice(IntRange(startIndex, endIndex))

    val containerBuilder = ComponentBuilder.ContainerBuilder.create()
        .addText(Utils.brBuilder(
            "## Centro de adoção",
            "Aqui estarão armazenado os pets jogados para adoção por outros players"
        ))
        .addDivider()

    petsInPage.forEach { pet ->
        containerBuilder.addSection(
            Button.primary("pet/adoptionCenter/adopt/${pet.userPet.id}", "Adotar"),
            Utils.brBuilder(
                "**Nome:** ${pet.userPet.name}",
                "**Gênero:** ${if(pet.userPet.gender === Gender.MALE) "Macho" else "Fêmea"}",
                "**Animal:** ${petAnimalFormatted[pet.pet.animal]}",
                "**Raridade:** ${petRarityFormatted[pet.pet.rarity]}",
                "**Espécie:** ${pet.pet.specie}",
                "**Personalidades:** ${pet.personality.joinToString(", ") { p -> p.get(PERSONALITYTRAIT.NAME)!! }}",
                "**ID:** ${pet.userPet.id}",
                "**Some:** ${Utils.formatDiscordTime(pet.adoptionCenter.deletein!!, "R")}"
            )
        ).addDivider()
    }

    containerBuilder.addRow(
        ActionRow.of(
            Button.secondary(
                "pet/adoptionCenter/page/${page - 1}",
                "Voltar"
            ).withDisabled(page == 0),
            Button.secondary(
                "pet/adoptionCenter/page/${page + 1}",
                "Avançar"
            ).withDisabled(endIndex >= pets.size)
        )
    )

    return mutableListOf(containerBuilder.build())
}