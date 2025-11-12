package studio.styx.erisbot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;

public class EmbedReply {
    public EmbedBuilder dangerBuilder(String message) {
        return new EmbedBuilder()
                .setDescription(message)
                .setColor(Color.RED);
    }
    public EmbedBuilder successBuilder(String message) {
        return new EmbedBuilder()
                .setDescription(message)
                .setColor(Color.GREEN);
    }
    public EmbedBuilder infoBuilder(String message) {
        return new EmbedBuilder()
                .setDescription(message)
                .setColor(Color.BLUE);
    }

    public MessageEmbed danger(String message) {
        return dangerBuilder(message).build();
    }

    public MessageEmbed success(String message) {
        return successBuilder(message).build();
    }

    public MessageEmbed info(String message) {
        return infoBuilder(message).build();
    }
}
