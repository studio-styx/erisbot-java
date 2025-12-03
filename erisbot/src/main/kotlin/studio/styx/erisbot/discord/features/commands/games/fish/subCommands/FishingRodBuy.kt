package studio.styx.erisbot.discord.features.commands.games.fish.subCommands

import database.extensions.getOrCreateUser
import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jooq.DSLContext
import shared.Colors
import shared.utils.Icon
import studio.styx.erisbot.generated.tables.references.FISHINGROD
import studio.styx.erisbot.generated.tables.references.USER
import studio.styx.erisbot.generated.tables.references.USERFISHINGROD
import studio.styx.schemaEXtended.core.interfaces.NumberType
import studio.styx.schemaEXtended.core.schemas.NumberSchema
import utils.ComponentBuilder
import java.time.LocalDateTime

suspend fun fishingRodBuyCommand(event: SlashCommandInteractionEvent, dsl: DSLContext) {
    val id = NumberSchema(NumberType.INT)
        .parseError("Retorne um id de vara válido!")
        .integer()
        .coerce()
        .parseOrThrow(event.getOption("id")?.asString)
        .toInt()

    event.deferReply().await()

    dsl.transaction { config ->
        run {
            val tx = config.dsl()

            val fishingRod = tx.selectFrom(FISHINGROD)
                .where(FISHINGROD.ID.eq(id))
                .fetchOne()

            if (fishingRod == null) {
                ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.DANGER)
                    .addText("${Icon.static.get("error")} | Não consegui encontrar essa vara de pesca!")
                    .reply(event)
                return@transaction
            }

            val user = tx.getOrCreateUser(event.user.id)

            if (user.money!!.toDouble() < fishingRod.price!!) {
                ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.DANGER)
                    .addText("${Icon.static.get("denied")} | Você não tem dinheiro suficiente para comprar essa vara de pesca!")
                    .reply(event)
                return@transaction
            }

            val alreadyHasRod = tx.selectFrom(USERFISHINGROD)
                .where(USERFISHINGROD.USERID.eq(event.user.id))
                .and(USERFISHINGROD.FISHINGRODID.eq(id))
                .fetchOne() != null

            if (alreadyHasRod) {
                ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.DANGER)
                    .addText("${Icon.static.get("denied")} | Você já possui essa vara de pesca!")
                    .reply(event)
                return@transaction
            }

            tx.insertInto(USERFISHINGROD)
                .set(USERFISHINGROD.USERID, event.user.id)
                .set(USERFISHINGROD.FISHINGRODID, id)
                .set(USERFISHINGROD.CREATEDAT, LocalDateTime.now())
                .set(USERFISHINGROD.DURABILITY, fishingRod.durability)
                .execute()
            tx.update(USER)
                .set(USER.MONEY, USER.MONEY.minus(fishingRod.price))
                .set(USER.UPDATEDAT, LocalDateTime.now())
                .where(USER.ID.eq(event.user.id))
                .execute()

            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.SUCCESS)
                .addText("${Icon.static.get("success")} | Você comprou a vara de pesca **${fishingRod.name}** por **${fishingRod.price}**")
                .reply(event)
        }
    }
}