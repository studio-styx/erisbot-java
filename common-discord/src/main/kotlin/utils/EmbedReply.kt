package utils

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import java.awt.Color

object Embed {
    @JvmStatic
    fun dangerBuilder(message: String?): EmbedBuilder {
        return EmbedBuilder()
            .setDescription(message)
            .setColor(Color.RED)
    }

    @JvmStatic
    fun successBuilder(message: String?): EmbedBuilder {
        return EmbedBuilder()
            .setDescription(message)
            .setColor(Color.GREEN)
    }

    @JvmStatic
    fun infoBuilder(message: String?): EmbedBuilder {
        return EmbedBuilder()
            .setDescription(message)
            .setColor(Color.BLUE)
    }

    @JvmStatic
    fun danger(message: String?): MessageEmbed {
        return dangerBuilder(message).build()
    }

    @JvmStatic
    fun success(message: String?): MessageEmbed {
        return successBuilder(message).build()
    }

    @JvmStatic
    fun info(message: String?): MessageEmbed {
        return infoBuilder(message).build()
    }
}