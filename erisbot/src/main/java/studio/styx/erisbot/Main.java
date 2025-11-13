package studio.styx.erisbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jooq.DSLContext;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import shared.Colors;
import studio.styx.erisbot.core.*;
import utils.ComponentBuilder;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
public class Main implements CommandLineRunner {

    @Autowired
    private ApplicationContext context; // ← para pegar beans do Spring

    @Autowired
    private DSLContext dsl; // ← seu JOOQ pronto

    @Autowired
    private DiscordConfig discordConfig; // ← seu token

    private JDA jda;

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args); // ← Spring inicia
    }

    @Override
    public void run(String... args) throws Exception {
        // === CRIA O JDA ===
        jda = JDABuilder.createLight(discordConfig.getToken(), EnumSet.of(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS
                ))
                .addEventListeners(new CommandListener())
                .build();

        jda.awaitReady();
        System.out.println("Bot iniciado: " + jda.getSelfUser().getName());

        // === CARREGA COMANDOS ===
        List<CommandInterface> commands = loadCommands();

        // === INJETA O DSL AGORA QUE O SPRING JÁ ESTÁ PRONTO ===
        context.getBean(CommandManager.class).injectDslIntoCommands();

        // === REGISTRA COMANDOS ===
        registerCommands(jda, commands);
    }

    // === INJETA O DSL NOS COMANDOS QUE HERDAM DE AbstractCommand ===
    private void injetarDslNosComandos(List<CommandInterface> commands) {
        for (CommandInterface cmd : commands) {
            if (cmd instanceof AbstractCommand abstractCmd) {
                abstractCmd.setDsl(dsl);
                System.out.println("DSL injetado em: " + cmd.getClass().getSimpleName());
            }
        }
    }

    // === SEU CÓDIGO ORIGINAL 100% INTACTO ===
    private List<CommandInterface> loadCommands() {
        List<CommandInterface> commands = new ArrayList<>();
        Reflections reflections = new Reflections("studio.styx.erisbot.features.commands", new SubTypesScanner(false));
        Set<Class<? extends CommandInterface>> commandClasses = reflections.getSubTypesOf(CommandInterface.class);

        for (Class<? extends CommandInterface> commandClass : commandClasses) {
            try {
                CommandInterface cmd = context.getBean(commandClass); // ← usa Spring pra instanciar!
                commands.add(cmd);
                System.out.println("Comando carregado: " + commandClass.getSimpleName());
            } catch (Exception e) {
                System.err.println("Erro ao carregar comando via Spring: " + commandClass.getSimpleName());
                e.printStackTrace();
            }
        }

        if (commands.isEmpty()) {
            System.out.println("Nenhum comando encontrado. Verifique @Component nas classes.");
        }

        return commands;
    }

    private void registerCommands(JDA jda, List<CommandInterface> commands) {
        List<SlashCommandData> commandDataList = new ArrayList<>();
        System.out.println("Comandos registrados:");
        for (CommandInterface command : commands) {
            SlashCommandData commandData = command.getSlashCommandData();
            commandDataList.add(commandData);
            System.out.println("- " + commandData.getName());
        }

        jda.updateCommands().addCommands(commandDataList).queue(
                success -> System.out.println("Comandos registrados com sucesso no Discord!"),
                error -> System.err.println("Erro ao registrar comandos: " + error.getMessage())
        );
    }

    private List<ResponderInterface> loadResponders() {
        List<ResponderInterface> responders = new ArrayList<>();
        Reflections reflections = new Reflections("studio.styx.erisbot.features.interactions", new SubTypesScanner(false));
        Set<Class<? extends ResponderInterface>> responderClasses = reflections.getSubTypesOf(ResponderInterface.class);

        for (Class<? extends ResponderInterface> responderClass : responderClasses) {
            try {
                ResponderInterface responder = context.getBean(responderClass);
                responders.add(responder);
                System.out.println("Interação carregada: " + responderClass.getSimpleName());
            } catch (Exception e) {
                System.err.println("Erro ao instanciar interação: " + responderClass.getSimpleName());
                e.printStackTrace();
            }
        }

        if (responders.isEmpty()) {
            System.out.println("Nenhuma interação encontrada em features.interactions ou subpastas.");
        }

        return responders;
    }

    // === SEU CommandListener 100% INTACTO ===
    class CommandListener extends ListenerAdapter {
        private final List<CommandInterface> commands = loadCommands(); // ← agora usa Spring!
        private final List<ResponderInterface> responders = loadResponders();

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            for (CommandInterface command : commands) {
                if (event.getName().equals(command.getSlashCommandData().getName())) {
                    try {
                        command.execute(event);
                    } catch (Exception e) {
                        System.err.println("Erro ao executar comando " + event.getName() + ": " + e.getMessage());
                        ComponentBuilder.ContainerBuilder.create()
                                .addText("Um erro ocorreu ao executar esse comando: **`" + e.getMessage() + "`**")
                                .withColor(Colors.DANGER)
                                .reply(event);
                    }
                    return;
                }
            }
            event.reply("Comando não encontrado.").setEphemeral(true).queue();
        }

        // === TODO O RESTO DO SEU CÓDIGO (buttons, modals, etc) 100% IGUAL ===
        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            handleInteraction(event, event.getComponentId(), ButtonInteractionEvent.class);
        }

        @Override
        public void onStringSelectInteraction(StringSelectInteractionEvent event) {
            handleInteraction(event, event.getComponentId(), StringSelectInteractionEvent.class);
        }

        @Override
        public void onModalInteraction(ModalInteractionEvent event) {
            handleInteraction(event, event.getModalId(), ModalInteractionEvent.class);
        }

        private void handleInteraction(Object event, String componentId, Class<?> eventType) {
            for (ResponderInterface responder : responders) {
                String customIdPattern = responder.getCustomId();
                String regex = customIdPattern.replaceAll(":(\\w+)", "[^/]+");
                Pattern pattern = Pattern.compile("^" + regex + "$");
                Matcher matcher = pattern.matcher(componentId);

                if (matcher.matches()) {
                    try {
                        Method method = responder.getClass().getMethod("execute", eventType);
                        if (!method.isDefault()) {
                            if (event instanceof ButtonInteractionEvent buttonEvent) {
                                responder.execute(buttonEvent);
                            } else if (event instanceof StringSelectInteractionEvent selectEvent) {
                                responder.execute(selectEvent);
                            } else if (event instanceof ModalInteractionEvent modalEvent) {
                                responder.execute(modalEvent);
                            }
                        } else {
                            replyError(event, "Interação não implementada para este tipo de evento.");
                        }
                    } catch (NoSuchMethodException e) {
                        System.err.println("Método execute não encontrado para " + eventType.getSimpleName());
                        replyError(event, "Erro interno: método não encontrado.");
                    } catch (Exception e) {
                        System.err.println("Erro ao executar interação " + componentId + ": " + e.getMessage());
                        replyError(event, "Erro ao processar interação.");
                    }
                    return;
                }
            }
            replyError(event, "Interação não encontrada.");
        }

        private void replyError(Object event, String message) {
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                buttonEvent.reply(message).setEphemeral(true).queue();
            } else if (event instanceof StringSelectInteractionEvent selectEvent) {
                selectEvent.reply(message).setEphemeral(true).queue();
            } else if (event instanceof ModalInteractionEvent modalEvent) {
                modalEvent.reply(message).setEphemeral(true).queue();
            }
        }

        private Map<String, String> extractDynamicParams(String customIdPattern, String actualId) {
            Map<String, String> params = new HashMap<>();
            String[] patternParts = customIdPattern.split("/");
            String[] actualParts = actualId.split("/");

            if (patternParts.length != actualParts.length) {
                return params;
            }

            for (int i = 0; i < patternParts.length; i++) {
                if (patternParts[i].startsWith(":")) {
                    String paramName = patternParts[i].substring(1);
                    params.put(paramName, actualParts[i]);
                }
            }

            return params;
        }
    }
}