package studio.styx.erisbot.features.interactions;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;
import studio.styx.erisbot.core.ResponderInterface;
import studio.styx.erisbot.utils.EmbedReply;
import utils.ComponentBuilder;

@Component
public class BotInfoResponders implements ResponderInterface {
    @Override
    public String getCustomId() {
        return "buttonTest/:button/:userId";
    }

    @Override
    public void execute(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        String userId = extractUserId(componentId);
        String button = extractButtonName(componentId);

        // Validação inicial
        if (userId == null || button == null) {
            System.err.println("Erro: customId malformado - " + componentId);
            event.reply("Erro: ID do botão inválido.").setEphemeral(true).queue();
            return;
        }

        // Verificar se o usuário é o correto
        if (!userId.equals(event.getUser().getId())) {
            EmbedReply res = new EmbedReply();
            event.replyEmbeds(res.danger("Você não é <@" + userId + "> para poder usar esse botão!"))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // Processar o botão
        if (button.equals("test1")) {
            ComponentBuilder.ContainerBuilder.create()
                    .addText("## Você apertou no botão!")
                    .addDivider(false)
                    .addText("Obrigado!")
                    .setEphemeral(true)
                    .withColor("#EB459E")
                    .reply(event);
        } else if (button.equals("test2")) {
            ComponentBuilder.ContainerBuilder container = ComponentBuilder.ContainerBuilder.create()
                    .addText("## Mensagem editada com sucesso!")
                    .withColor("#EB459E");

            event.editComponents(container.build())
                    .useComponentsV2()
                    .queue();
        } else {
            System.err.println("Erro: botão desconhecido - " + button);
            event.reply("Erro: botão não reconhecido.").setEphemeral(true).queue();
        }
    }

    private String extractUserId(String customId) {
        try {
            String[] parts = customId.split("/");
            return parts.length == 3 ? parts[2] : null;
        } catch (Exception e) {
            System.err.println("Erro ao extrair userId de " + customId + ": " + e.getMessage());
            return null;
        }
    }

    private String extractButtonName(String customId) {
        try {
            String[] parts = customId.split("/");
            return parts.length == 3 ? parts[1] : null;
        } catch (Exception e) {
            System.err.println("Erro ao extrair buttonName de " + customId + ": " + e.getMessage());
            return null;
        }
    }
}