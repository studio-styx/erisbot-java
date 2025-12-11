package studio.styx.erisbot.discord.features.commands.moderation.giveaway

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.Env
import shared.utils.Icon
import shared.utils.Utils
import studio.styx.erisbot.core.extensions.jda.guilds.giveawayEntryEndPoints.giveawayEntryPoints
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.core.interfaces.CommandInterface
import studio.styx.erisbot.discord.features.commands.moderation.giveaway.subCommands.cancelGiveawayCommand
import studio.styx.erisbot.discord.features.commands.moderation.giveaway.subCommands.endGiveawayCommand
import studio.styx.erisbot.discord.features.commands.moderation.giveaway.subCommands.rerollGiveawayCommand
import studio.styx.erisbot.generated.tables.records.UsergiveawayRecord
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.erisbot.generated.tables.references.GUILDGIVEAWAY
import studio.styx.erisbot.generated.tables.references.USERGIVEAWAY
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Component
class GiveawayCommands : CommandInterface {
    @Autowired
    lateinit var dsl: DSLContext

    val possiblePermissions = listOf<Permission>(
        Permission.MANAGE_SERVER,
        Permission.MANAGE_EVENTS,
        Permission.ADMINISTRATOR
    )

    override fun getSlashCommandData(): SlashCommandData {
        val createGiveaway = SubcommandData("create", "Create a new giveaway")
        val endGiveaway = SubcommandData("end", "end a giveaway")
            .addOptions(
                OptionData(OptionType.STRING, "id", "giveaway id", true)
                    .setAutoComplete(true)
            )
        val cancelGiveaway = SubcommandData("cancel", "cancel a giveaway")
            .addOptions(
                OptionData(OptionType.STRING, "id", "giveaway id", true)
                    .setAutoComplete(true)
            )
        val editGiveaway = SubcommandData("edit", "edit a giveaway")
            .addOptions(
                OptionData(OptionType.STRING, "id", "giveaway id", true)
                    .setAutoComplete(true)
            )
        val rerollUser = SubcommandData("reroll", "reroll a giveaway")
            .addOptions(
                OptionData(OptionType.STRING, "giveaway", "giveaway id", true)
                    .setAutoComplete(true),
                OptionData(OptionType.STRING, "user", "user id", true)
                    .setAutoComplete(true)
            )
        val entryConnectedGiveaway = SubcommandData("entry", "entry in a connected giveaway")
            .addOptions(
                OptionData(OptionType.STRING, "id", "giveaway id", true)
                    .setAutoComplete(true),
                OptionData(OptionType.CHANNEL, "channel", "channel where the giveaway will to be entered", true)
                    .setAutoComplete(true)
                    .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
            )


        return Commands.slash("giveaway", "giveaway commands")
            .addSubcommands(createGiveaway, endGiveaway, cancelGiveaway, editGiveaway, rerollUser, entryConnectedGiveaway)
    }

    override suspend fun onAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        val hasPerms = hasPermission(event.member!!)

        if (!hasPerms) {
            event.replyChoice("Você não tem permissão pra gerenciar sorteios!", "null")
            return
        }

        val subCommand = event.subcommandName
        val focused = event.focusedOption
        val guildId = event.guild!!.id

        when (subCommand) {
            "end", "cancel", "edit" -> {
                val giveaways = getGiveaways(guildId)

                val choices = giveaways
                    .filter {
                        val title = it.get(GIVEAWAY.TITLE)
                        title != null && title.contains(focused.value, true)
                    }
                    .map { g ->
                        val giveawayName = g.get(GIVEAWAY.TITLE)!!
                        Command.Choice(
                            Utils.limitText(giveawayName, 97, "..."),
                            g.get(GIVEAWAY.ID).toString()
                        )
                    }
                    .take(25)

                event.replyChoices(choices).await()
            }
            "reroll" -> {
                if (focused.name == "giveaway") {
                    val giveaways = getGiveaways(guildId, true, 4)

                    val choices = giveaways
                        .filter {
                            val title = it.get(GIVEAWAY.TITLE)
                            title != null && title.contains(focused.value, true)
                        }
                        .map { g ->
                            val giveawayName = g.get(GIVEAWAY.TITLE)!!
                            Command.Choice(
                                Utils.limitText(giveawayName, 97, "..."),
                                g.get(GIVEAWAY.ID).toString()
                            )
                        }
                        .take(25)

                    event.replyChoices(choices).await()
                } else {
                    // Obter o ID do giveaway da opção focada ou do parâmetro giveaway
                    val giveawayId = if (focused.name == "winner") {
                        // Se a opção focada é "winner", precisamos do ID do giveaway
                        event.getOption("giveaway")?.asString?.toIntOrNull()
                    } else {
                        focused.value.toIntOrNull()
                    }

                    if (giveawayId == null) {
                        event.replyChoice("Invalid giveaway ID", "none")
                        return
                    }

                    val participants = getWinners(giveawayId)
                    val names = getParticipantsNames(participants, event, giveawayId)

                    val choices = names
                        .filter { n ->
                            n.values.first().contains(focused.value, true)
                        }
                        .map { n ->
                            Command.Choice(
                                Utils.limitText(n.values.first(), 97, "..."),
                                n.keys.first()
                            )
                        }
                        .take(25)

                    event.replyChoices(choices).await()
                }
            }
            "entry" -> {
                val invites = event.guild!!.giveawayEntryPoints.getEntryInvites()

                val giveaways = dsl.selectFrom(GIVEAWAY)
                    .where(GIVEAWAY.ID.`in`(invites.map { it.giveawayId }))
                    .fetch()

                val choices = giveaways
                    .filter {
                        it.title!!.contains(focused.name, true)
                    }
                    .map { g ->
                        val invite = invites.find { it.giveawayId == g.id }
                        val guild = event.jda.getGuildById(invite?.inviterGuildId ?: "2")
                        val choice = Command.Choice(
                            Utils.limitText("${guild?.name ?: "Desconhecido"} (${g.title})", 97, "..."),
                            g.id.toString()
                        )
                        choice
                    }
                    .take(25)

                event.replyChoices(choices).await()
            }
        }
    }

    private fun getWinners(giveawayId: Int): List<UsergiveawayRecord> {
        return dsl.selectFrom(USERGIVEAWAY)
            .where(USERGIVEAWAY.GIVEAWAYID.eq(giveawayId))
            .and(USERGIVEAWAY.ISWINNER.eq(true))
            .fetchInto(UsergiveawayRecord::class.java)
    }

    private suspend fun getParticipantsNames(
        participants: List<UsergiveawayRecord>,
        event: CommandAutoCompleteInteractionEvent,
        giveawayId: Int
    ): List<Map<String, String>> {
        val key = "giveaway:participants:$giveawayId"
        val cached = Cache.get<List<Map<String, String>>>(key)

        if (cached != null) return cached

        val names = participants.mapNotNull { p ->
            val user = event.jda.getUserById(p.userid!!) ?: run {
                try {
                    event.jda.retrieveUserById(p.userid!!).await()
                } catch (e: Exception) {
                    null
                }
            }
            user?.let { mapOf(it.id to "${it.name} (${it.id})") }
        }

        Cache.set(key, names, 10, TimeUnit.MINUTES)
        return names
    }

    private fun getGiveaway(id: Int): Record? {
        return dsl.select(GUILDGIVEAWAY.asterisk(), GIVEAWAY.asterisk())
            .from(GIVEAWAY)
            .innerJoin(GUILDGIVEAWAY).on(GIVEAWAY.ID.eq(GUILDGIVEAWAY.GIVEAWAYID))
            .where(GIVEAWAY.ID.eq(id))
            .fetchOne()
    }

    private fun getGiveawayWithParticipants(id: Int): List<Record> {
        return dsl.select(GUILDGIVEAWAY.asterisk(), GIVEAWAY.asterisk(), USERGIVEAWAY.asterisk())
            .from(GIVEAWAY)
            .innerJoin(GUILDGIVEAWAY).on(GIVEAWAY.ID.eq(GUILDGIVEAWAY.GIVEAWAYID))
            .innerJoin(USERGIVEAWAY).on(GIVEAWAY.ID.eq(USERGIVEAWAY.GIVEAWAYID))
            .where(GIVEAWAY.ID.eq(id))
            .fetch()
    }

    private fun getGiveaways(
        guildId: String,
        ended: Boolean? = false,
        daysBefore: Int? = null,
        daysAfter: Int? = null
    ): List<Record> {
        val query = dsl.select(GUILDGIVEAWAY.asterisk(), GIVEAWAY.asterisk())
            .from(GIVEAWAY)
            .innerJoin(GUILDGIVEAWAY).on(GIVEAWAY.ID.eq(GUILDGIVEAWAY.GIVEAWAYID))
            .where(GUILDGIVEAWAY.GUILDID.eq(guildId))

        if (ended != null) {
            query.and(GIVEAWAY.ENDED.eq(ended))
        }
        if (daysAfter != null) {
            query.and(GIVEAWAY.CREATEDAT.greaterThan(LocalDateTime.now().minusDays(daysAfter.toLong())))
        }
        if (daysBefore != null) {
            query.and(GIVEAWAY.EXPIRESAT.lessThan(LocalDateTime.now().plusDays(daysBefore.toLong())))
        }

        return query.fetch()
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        val hasPerms = hasPermission(event.member!!)

        if (!hasPerms) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Você não tem permissão pra gerenciar sorteios!", true)
            return
        }

        val frontUrl = Env.get("FRONT_URL", "https://erisbot.squareweb.app")

        val subCommand = event.subcommandName
        when (subCommand) {
            "reroll" -> rerollGiveawayCommand(event, dsl)
            "cancel" -> cancelGiveawayCommand(event, dsl)
            "end" -> endGiveawayCommand(event, dsl)
            "create" -> {
                val guildId = event.guild!!.id
                val redirectUrl = "$frontUrl/$guildId/giveaways/create"

                event.rapidContainerReply(Colors.WARNING,
                    "${Icon.static.get("Eris_happy")} | Agora a criação de sorteios e feita via website! **[Clique aqui para ser redirecionado para a criação de sorteios!]($redirectUrl)**",
                    true
                )
            }
            "edit" -> {
                val guildId = event.guild!!.id
                val giveawayId = event.getOption("id")!!.asString
                val redirectUrl = "$frontUrl/$guildId/giveaways/edit/$giveawayId"

                event.rapidContainerReply(Colors.WARNING,
                    "${Icon.static.get("Eris_happy")} | Agora a edição de sorteios e feita via website! **[Clique aqui para ser redirecionado para a edição de sorteios!]($redirectUrl)**",
                    true
                )
            }
        }
    }

    private fun hasPermission(member: Member): Boolean {
        var hasPermission = false

        for (p in member.permissions) {
            if (possiblePermissions.contains(p)) {
                hasPermission = true
                break
            }
        }

        return hasPermission
    }
}