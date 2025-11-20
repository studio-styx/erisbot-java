package studio.styx.erisbot.features.commands.economy.cassino;

import database.utils.DatabaseUtils;
import database.utils.LogManage;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Colors;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.enums.Rarity;
import studio.styx.erisbot.generated.tables.records.PetRecord;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.records.UserpetskillRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import translates.TranslatesObjects;
import translates.commands.economy.cassino.HorseRacingTranslateInterface;
import utils.ComponentBuilder;

import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class HorseRacing implements CommandInterface {

    @Autowired
    private DSLContext dsl;

    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Bônus por raridade para luck
    private final Map<Rarity, Double> rarityLuckBonus = Map.of(
            Rarity.COMUM, 0.02,
            Rarity.UNCOMUM, 0.04,
            Rarity.RARE, 0.06,
            Rarity.EPIC, 0.08,
            Rarity.LEGENDARY, 0.10
    );

    // Bônus por raridade para amount
    private final Map<Rarity, Double> rarityAmountBonus = Map.of(
            Rarity.COMUM, 0.1,
            Rarity.UNCOMUM, 0.2,
            Rarity.RARE, 0.3,
            Rarity.EPIC, 0.4,
            Rarity.LEGENDARY, 0.5
    );

    private Map<String, Horse> horses;
    private String selectedHorse;
    private double amount;
    private double winMultiplier = 1.5;

    @Override
    public SlashCommandData getSlashCommandData() {
        OptionData horce = new OptionData(OptionType.STRING, "horse", "Horse to bet on", true)
                .addChoices(
                        new Command.Choice("Purple", "purple")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "roxo")
                                .setNameLocalization(DiscordLocale.SPANISH, "morado"),
                        new Command.Choice("Blue", "blue")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "azul")
                                .setNameLocalization(DiscordLocale.SPANISH, "azul"),
                        new Command.Choice("Green", "green")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "verde")
                                .setNameLocalization(DiscordLocale.SPANISH, "verde"),
                        new Command.Choice("Yellow", "yellow")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "amarelo")
                                .setNameLocalization(DiscordLocale.SPANISH, "amarillo"),
                        new Command.Choice("Orange", "orange")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "laranja")
                                .setNameLocalization(DiscordLocale.SPANISH, "naranja"),
                        new Command.Choice("Red", "red")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "vermelho")
                                .setNameLocalization(DiscordLocale.SPANISH, "rojo"),
                        new Command.Choice("Pink", "pink")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "rosa")
                                .setNameLocalization(DiscordLocale.SPANISH, "rosa"),
                        new Command.Choice("Brown", "brown")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "marrom")
                                .setNameLocalization(DiscordLocale.SPANISH, "marrón")
                );

        OptionData amount = new OptionData(OptionType.NUMBER, "amount", "amount to bet", true)
                .setMinValue(50)
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "valor")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "valor");

        return Commands.slash("horse-racing", "Bet on horse racing")
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "corrida-de-cavalos")
                .setNameLocalization(DiscordLocale.SPANISH, "carreras-de-caballos")
                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "apostar na corrida de cavalos")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "apostar en carreras de caballos")
                .addOptions(horce, amount);

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue(hook -> dsl.transaction(config -> {
            DSLContext tx = config.dsl();
            String userId = event.getUser().getId();

            // Obtém as opções
            this.amount = event.getOption("amount").getAsDouble();
            this.selectedHorse = event.getOption("horse").getAsString();

            UserRecord userData = DatabaseUtils.getOrCreateUser(tx, userId);
            HorseRacingTranslateInterface t = TranslatesObjects.getHorseRacing(event.getUserLocale().getLocale());

            // Inicializa os cavalos
            initializeHorses(t);

            // Verifica se tem dinheiro suficiente
            if (amount < 50) {
                hook.editOriginalComponents(
                        ComponentBuilder.ContainerBuilder.create()
                                .addText(t.getNotEnoughMoney())
                                .withColor(Colors.DANGER)
                                .build()
                ).useComponentsV2().queue();
                return;
            }

            // Ajusta o amount se for maior que o dinheiro disponível
            if (userData.getMoney().doubleValue() < amount) {
                this.amount = userData.getMoney().doubleValue();
            }

            // Busca o pet ativo e suas skills
            PetRecord activePet = getActivePetWithSkills(tx, userId);

            // Encontra as skills
            UserpetskillRecord horseRacingLuck = findHorseRacingSkill(activePet, tx, "horse_racing_luck");
            UserpetskillRecord horseRacingBonus = findHorseRacingSkill(activePet, tx, "horse_racing_bonus");

            // Calcula a chance do cavalo apostado ganhar
            double userHorseChance = calculateWinChance(activePet, horseRacingLuck);

            // Inicia a corrida
            startRace(event, hook, tx, userId, t, activePet, horseRacingBonus, userHorseChance);
        }));
    }

    private void initializeHorses(HorseRacingTranslateInterface t) {
        horses = new HashMap<>();
        horses.put("purple", new Horse(t.getHorses().getPurple().getName(), t.getHorses().getPurple().getEmoji(), t.getHorses().getPurple().getColorEmoji()));
        horses.put("blue", new Horse(t.getHorses().getBlue().getName(), t.getHorses().getBlue().getEmoji(), t.getHorses().getBlue().getColorEmoji()));
        horses.put("green", new Horse(t.getHorses().getGreen().getName(), t.getHorses().getGreen().getEmoji(), t.getHorses().getGreen().getColorEmoji()));
        horses.put("yellow", new Horse(t.getHorses().getYellow().getName(), t.getHorses().getYellow().getEmoji(), t.getHorses().getYellow().getColorEmoji()));
        horses.put("orange", new Horse(t.getHorses().getOrange().getName(), t.getHorses().getOrange().getEmoji(), t.getHorses().getOrange().getColorEmoji()));
        horses.put("red", new Horse(t.getHorses().getRed().getName(), t.getHorses().getRed().getEmoji(), t.getHorses().getRed().getColorEmoji()));
        horses.put("pink", new Horse(t.getHorses().getPink().getName(), t.getHorses().getPink().getEmoji(), t.getHorses().getPink().getColorEmoji()));
        horses.put("brown", new Horse(t.getHorses().getBrown().getName(), t.getHorses().getBrown().getEmoji(), t.getHorses().getBrown().getColorEmoji()));
    }

    private PetRecord getActivePetWithSkills(DSLContext tx, String userId) {
        return tx.select(TablesKt.getPET().asterisk())
                .from(TablesKt.getPET())
                .join(TablesKt.getUSER()).on(TablesKt.getUSER().getACTIVEPETID().eq(TablesKt.getPET().getID()))
                .where(TablesKt.getUSER().getID().eq(userId))
                .fetchOneInto(PetRecord.class);
    }

    private UserpetskillRecord findHorseRacingSkill(PetRecord activePet, DSLContext tx, String skillName) {
        if (activePet == null) return null;

        return tx.select(TablesKt.getUSERPETSKILL().asterisk())
                .from(TablesKt.getUSERPETSKILL())
                .join(TablesKt.getPETSKILL()).on(TablesKt.getUSERPETSKILL().getSKILLID().eq(TablesKt.getPETSKILL().getID()))
                .where(TablesKt.getUSERPETSKILL().getUSERPETID().eq(activePet.getId())
                        .and(TablesKt.getPETSKILL().getNAME().eq(skillName)))
                .fetchOneInto(UserpetskillRecord.class);
    }

    private double calculateWinChance(PetRecord activePet, UserpetskillRecord horseRacingLuck) {
        double baseWinChance = 0.2; // 20% base
        double userHorseChance = baseWinChance;

        if (horseRacingLuck != null && activePet != null) {
            userHorseChance += rarityLuckBonus.getOrDefault(activePet.getRarity(), 0.0) + (horseRacingLuck.getLevel() * 0.02);
        }

        return Math.min(userHorseChance, 0.5); // Máximo 50%
    }

    private void startRace(SlashCommandInteractionEvent event, InteractionHook hook,
                           DSLContext tx, String userId, HorseRacingTranslateInterface t,
                           PetRecord activePet, UserpetskillRecord horseRacingBonus, double userHorseChance) {

        // Embed inicial
        MessageEmbed initialEmbed = createRaceEmbed(t, false, null, 1.5);
        hook.editOriginalEmbeds(initialEmbed).queue();

        // Inicia a corrida após 2 segundos
        scheduler.schedule(() -> moveHorses(event, hook, tx, userId, t, activePet, horseRacingBonus, userHorseChance, 0), 2, TimeUnit.SECONDS);
    }

    private void moveHorses(SlashCommandInteractionEvent event, InteractionHook hook,
                            DSLContext tx, String userId, HorseRacingTranslateInterface t,
                            PetRecord activePet, UserpetskillRecord horseRacingBonus, double userHorseChance, int round) {

        // Determina o vencedor predeterminado
        String predeterminedWinner = determineWinner(userHorseChance);

        // Move os cavalos
        boolean raceFinished = moveAllHorses(predeterminedWinner);

        // Atualiza a mensagem
        MessageEmbed raceEmbed = createRaceEmbed(t, false, null, 1.5);
        hook.editOriginalEmbeds(raceEmbed).queue();

        // Verifica se a corrida terminou
        String actualWinner = getActualWinner();
        if (raceFinished && actualWinner != null) {
            boolean userWon = actualWinner.equals(selectedHorse);

            // Calcula o multiplicador se ganhou
            if (userWon) {
                calculateWinMultiplier(activePet, horseRacingBonus);
            }

            // Atualiza o dinheiro do usuário
            updateUserBalance(tx, userId, userWon);

            // Cria embed final
            MessageEmbed finalEmbed = createRaceEmbed(t, true, actualWinner, userWon ? winMultiplier : 1.5);
            hook.editOriginalEmbeds(finalEmbed).queue();

            // Registra log
            registerLog(event, userWon, actualWinner);

            return;
        }

        // Continua a corrida se não terminou
        if (!raceFinished) {
            scheduler.schedule(() -> moveHorses(event, hook, tx, userId, t, activePet, horseRacingBonus, userHorseChance, round + 1), 2, TimeUnit.SECONDS);
        }
    }

    private String determineWinner(double userHorseChance) {
        if (random.nextDouble() < userHorseChance) {
            return selectedHorse;
        }

        // Sorteia entre os outros cavalos
        List<String> otherHorses = new ArrayList<>(horses.keySet());
        otherHorses.remove(selectedHorse);
        return otherHorses.get(random.nextInt(otherHorses.size()));
    }

    private boolean moveAllHorses(String predeterminedWinner) {
        boolean raceFinished = false;

        for (String horseKey : horses.keySet()) {
            Horse horse = horses.get(horseKey);

            double moveChance = 0.7; // Chance base
            int moveDistance = 1 + random.nextInt(2); // 1-2 posições

            // Vantagem para o vencedor predeterminado
            if (horseKey.equals(predeterminedWinner)) {
                moveChance = 0.9;
                moveDistance = 1 + random.nextInt(3); // 1-3 posições
            }

            if (random.nextDouble() < moveChance) {
                horse.position += moveDistance;
                if (horse.position >= 14) {
                    raceFinished = true;
                }
            }
        }

        return raceFinished;
    }

    private String getActualWinner() {
        for (Map.Entry<String, Horse> entry : horses.entrySet()) {
            if (entry.getValue().position >= 14) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void calculateWinMultiplier(PetRecord activePet, UserpetskillRecord horseRacingBonus) {
        winMultiplier = 1.5; // Multiplicador base

        if (horseRacingBonus != null && activePet != null) {
            winMultiplier += rarityAmountBonus.getOrDefault(activePet.getRarity(), 0.0) + (horseRacingBonus.getLevel() * 0.05);
            winMultiplier = Math.min(winMultiplier, 3.0); // Limite máximo de 3x
        }
    }

    private void updateUserBalance(DSLContext tx, String userId, boolean userWon) {
        if (userWon) {
            double winAmount = amount * winMultiplier;
            tx.update(TablesKt.getUSER())
                    .set(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getMONEY().add(BigDecimal.valueOf(winAmount)))
                    .where(TablesKt.getUSER().getID().eq(userId))
                    .execute();
        } else {
            tx.update(TablesKt.getUSER())
                    .set(TablesKt.getUSER().getMONEY(), TablesKt.getUSER().getMONEY().subtract(BigDecimal.valueOf(amount)))
                    .where(TablesKt.getUSER().getID().eq(userId))
                    .execute();
        }
    }

    private MessageEmbed createRaceEmbed(HorseRacingTranslateInterface t, boolean isFinished, String winner, double multiplier) {
        EmbedBuilder builder = new EmbedBuilder();

        if (isFinished && winner != null) {
            builder.setTitle(t.getEnd().getTitle())
                    .setDescription(createRaceTrack())
                    .setColor(Color.decode(winner.equals(selectedHorse) ? Colors.SUCCESS : Colors.DANGER))
                    .addField(t.getEnd().getFields().getWinner().getName(),
                            t.getEnd().getFields().getWinner().value(horses.get(winner).emoji, horses.get(winner).name), true)
                    .addField(t.getEnd().getFields().getBet().getName(),
                            t.getEnd().getFields().getBet().value(horses.get(selectedHorse).emoji, horses.get(selectedHorse).name), true)
                    .addField(t.getEnd().getFields().getResult().getName(),
                            t.getEnd().getFields().getResult().value(winner.equals(selectedHorse), amount, multiplier), false);
        } else {
            builder.setTitle(t.getPlaying().getTitle())
                    .setDescription(createRaceTrack())
                    .setColor(Color.decode(Colors.PRIMARY));
        }

        return builder.build();
    }

    private String createRaceTrack() {
        StringBuilder description = new StringBuilder();
        final int trackLength = 15;

        for (Horse horse : horses.values()) {
            String progress = "―".repeat(trackLength);
            int position = Math.min(horse.position, trackLength - 1);

            // Constrói a pista como string diretamente
            String track = progress.substring(0, position) +
                    horse.emoji +
                    progress.substring(position + 1);

            description.append("**")
                    .append(horse.name)
                    .append("** ")
                    .append(track)
                    .append(" ")
                    .append(horse.colorEmoji)
                    .append("\n");
        }

        return description.toString();
    }

    private void registerLog(SlashCommandInteractionEvent event, boolean userWon, String winner) {
        String logMessage;
        List<String> tags = new ArrayList<>(Arrays.asList("cassino", "transaction", "horse-racing"));

        if (userWon) {
            logMessage = String.format("Horse Racing WIN | Horse: %s | Bet: %.2f | Multiplier: %.1fx | Win: %.2f",
                    horses.get(winner).name, amount, winMultiplier, amount * winMultiplier);
            tags.add("sum");
        } else {
            logMessage = String.format("Horse Racing LOSE | Horse: %s | Bet: %.2f | Winner: %s",
                    horses.get(selectedHorse).name, amount, horses.get(winner).name);
            tags.add("sub");
        }

        LogManage.CreateLog.create()
                .setUserId(event.getUser().getId())
                .setMessage(logMessage)
                .setLevel(6)
                .setTags(tags);
    }

    // Classe auxiliar para representar um cavalo
    private static class Horse {
        String name;
        String emoji;
        String colorEmoji;
        int position = 0;

        Horse(String name, String emoji, String colorEmoji) {
            this.name = name;
            this.emoji = emoji;
            this.colorEmoji = colorEmoji;
        }
    }
}