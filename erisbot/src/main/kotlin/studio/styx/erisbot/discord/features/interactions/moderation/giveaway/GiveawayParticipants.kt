package studio.styx.erisbot.discord.features.interactions.moderation.giveaway

import database.extensions.giveaway
import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.components.filedisplay.FileDisplay
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.utils.FileUpload
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Icon
import discord.extensions.jda.reply.rapidContainerReply
import discord.extensions.jda.users.getOrRetrieveUserOrNullAsync
import studio.styx.erisbot.core.interfaces.ResponderInterface
import utils.ComponentBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class GiveawayParticipants : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "giveaway/participants/:giveawayId"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val params = CustomIdHelper(customId, event.customId)
        val giveawayId = params.getAsInt("giveawayId")!!

        event.deferReply(true).await()

        val result = dsl.giveaway(giveawayId).withParticipants().fetch() ?: run {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("error")} | Eu procurei por toda parte mas não consegui encontrar esse sorteio!",
                true
            )
            return
        }

        val participants = result.participants!!
        val giveaway = result.giveaway

        if (participants.isEmpty()) {
            event.rapidContainerReply(
                Colors.DANGER,
                "${Icon.static.get("Eris_cry")} | Ninguém por aqui ainda... que tal ser o primeiro?"
            )
            return
        }

        val guildLocale = event.guild?.locale ?: DiscordLocale.ENGLISH_US
        val title = giveaway.title ?: "Sorteio sem título"

        if (participants.size > 20) {

            // --- LÓGICA DE DATA E FUSO HORÁRIO ---

            // 1. Definir o Fuso Horário baseado no Locale do Discord
            // Como DiscordLocale não tem timezone, fazemos uma suposição baseada no idioma
            val timeZone = when (guildLocale) {
                DiscordLocale.PORTUGUESE_BRAZILIAN -> ZoneId.of("America/Sao_Paulo")
                DiscordLocale.ENGLISH_UK -> ZoneId.of("Europe/London")
                DiscordLocale.JAPANESE -> ZoneId.of("Asia/Tokyo")
                DiscordLocale.FRENCH -> ZoneId.of("Europe/Paris")
                DiscordLocale.ITALIAN -> ZoneId.of("Europe/Rome")
                DiscordLocale.GERMAN -> ZoneId.of("Europe/Berlin")
                DiscordLocale.RUSSIAN -> ZoneId.of("Europe/Moscow")
                DiscordLocale.CHINESE_CHINA -> ZoneId.of("Asia/Shanghai")
                DiscordLocale.KOREAN -> ZoneId.of("Asia/Seoul")
                DiscordLocale.HUNGARIAN -> ZoneId.of("Europe/Budapest")
                DiscordLocale.INDONESIAN -> ZoneId.of("Asia/Jakarta")
                DiscordLocale.VIETNAMESE -> ZoneId.of("Asia/Ho_Chi_Minh")
                DiscordLocale.THAI -> ZoneId.of("Asia/Bangkok")
                else -> ZoneId.of("UTC")
            }

            // 2. Converter o Locale do Discord para Java Locale (para traduzir meses/dias)
            val javaLocale = Locale.forLanguageTag(guildLocale.locale)

            // 3. Formatador
            // O 'z' no final coloca a sigla do fuso (ex: BRT, UTC, GMT)
            val formatter = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy 'às' HH:mm (z)")
                .withLocale(javaLocale)
                .withZone(timeZone)

            val formattedDate = giveaway.expiresat?.atZone(ZoneOffset.UTC)?.withZoneSameInstant(timeZone)?.format(formatter)
                ?: "Data indefinida"

            // -------------------------------------

            // Criar arquivo TXT
            val txtContent = buildString {
                appendLine("- - - - - - SORTEIO: $title - - - - - - -")
                appendLine("Data de expiração: $formattedDate")
                appendLine("Fuso horário utilizado: ${timeZone.id}")
                appendLine("ID do sorteio: ${giveaway.id}")
                appendLine()
                appendLine("PARTICIPANTES:")
                appendLine()

                participants.forEachIndexed { index, participant ->
                    val userId = participant.userid!!
                    val effectiveName = event.jda.getOrRetrieveUserOrNullAsync(userId)?.effectiveName ?: "Usuário desconhecido"
                    appendLine("${index + 1}. $effectiveName - $userId")
                }

                appendLine()
                appendLine("- - - - - - TOTAL: ${participants.size} participantes - - - - - - -")
            }

            val timestamp = Instant.now().epochSecond
            val fileName = "sorteio_${giveaway.id}_${timestamp}.txt"
            val fileData = txtContent.toByteArray()
            val fileUpload = FileUpload.fromData(fileData, fileName)

            ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.SUCCESS)
                .addText("## 📋 Lista de participantes do sorteio")
                .addText("**${Icon.static.get("info")} | Há ${participants.size} participantes.**")
                .addText("A lista completa foi enviada como arquivo anexo.")
                .addDivider()
                .add(FileDisplay.fromFile(fileUpload))
                .reply(event)

            return
        }

        // Se tiver 20 ou menos participantes...
        val participantList = buildString {
            participants.forEachIndexed { index, participant ->
                val userId = participant.userid!!
                val effectiveName = event.jda.getOrRetrieveUserOrNullAsync(userId)?.effectiveName ?: "Usuário desconhecido"

                append("**${index + 1}.** $effectiveName (`$userId`)\n")
            }
        }

        event.rapidContainerReply(
            Colors.PRIMARY,
            "${Icon.static.get("users")} | **Participantes do sorteio: $title**\n\n" +
                    "**Total de participantes:** ${participants.size}\n\n" +
                    "**Lista de participantes:**\n$participantList",
            false
        )
    }
}