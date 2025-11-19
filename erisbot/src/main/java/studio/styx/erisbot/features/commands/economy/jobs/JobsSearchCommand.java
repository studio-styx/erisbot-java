package studio.styx.erisbot.features.commands.economy.jobs;

import database.utils.DatabaseUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Colors;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.enums.Contractstatus;
import studio.styx.erisbot.generated.tables.records.CompanyRecord;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import studio.styx.erisbot.menus.economy.workSystem.JobsSearch;
import utils.ComponentBuilder;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class JobsSearchCommand implements CommandInterface {
    @Autowired
    private DSLContext dsl;

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("job", "💼 ✦ Search for a job or get dismiss")
                .addSubcommands(
                        new SubcommandData("search", "🔍 ✦ Search for a job")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "procurar")
                                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "🔍 ✦ Procurar por um emprego")
                                .setNameLocalization(DiscordLocale.SPANISH, "buscar")
                                .setDescriptionLocalization(DiscordLocale.SPANISH, "🔍 ✦ Buscar un trabajo")
                                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "buscar")
                                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "🔍 ✦ Buscar un trabajo"),
                        new SubcommandData("dismiss", "🚪 ✦ Get dismiss from your job")
                                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "demitir")
                                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "🚪 ✦ Ser demitido do seu emprego")
                                .setNameLocalization(DiscordLocale.SPANISH, "despedir")
                                .setDescriptionLocalization(DiscordLocale.SPANISH, "🚪 ✦ Ser despedido de tu trabajo")
                                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "despedir")
                                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "🚪 ✦ Ser despedido de tu trabajo")
                )
                .setNameLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "emprego")
                .setDescriptionLocalization(DiscordLocale.PORTUGUESE_BRAZILIAN, "💼 ✦ Procure por um emprego ou seja demitido")
                .setNameLocalization(DiscordLocale.SPANISH, "trabajo")
                .setDescriptionLocalization(DiscordLocale.SPANISH, "💼 ✦ Busca un trabajo o renuncia")
                .setNameLocalization(DiscordLocale.SPANISH_LATAM, "trabajo")
                .setDescriptionLocalization(DiscordLocale.SPANISH_LATAM, "💼 ✦ Busca un trabajo o renuncia")
                .setNameLocalization(DiscordLocale.ENGLISH_US, "job")
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, "💼 ✦ Search for a job or get dismiss");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        switch (event.getSubcommandName()) {
            case "search" -> search(event);
            case "dismiss" -> dismiss(event);
            default -> notFound(event);
        }
    }
    
    private void search(SlashCommandInteractionEvent event) {
        event.deferReply().queue(hook -> {
            List<CompanyRecord> companys = dsl.selectFrom(TablesKt.getCOMPANY())
                    .where(TablesKt.getCOMPANY().getISENABLED().eq(true))
                    .orderBy(
                            TablesKt.getCOMPANY().getEXPERIENCE().asc(),
                            TablesKt.getCOMPANY().getDIFFICULTY().asc(),
                            TablesKt.getCOMPANY().getWAGE().desc()
                    )
                    .fetch();

            JobsSearch menuContext = new JobsSearch();

            hook.editOriginalComponents(menuContext.jobsContainer(event.getUser().getId(), companys, 1)).useComponentsV2().queue();
        });
    }
    
    private void dismiss(SlashCommandInteractionEvent event) {
        event.deferReply().queue(hook -> {
            UserRecord user = DatabaseUtils.getOrCreateUser(dsl, event.getUser().getId());
            
            Integer contractId = user.getContractid();
            
            if (contractId == null) {
                ComponentBuilder.ContainerBuilder.create()
                        .setEphemeral(true)
                        .withColor(Colors.DANGER)
                        .addText("Você não tem um emprego para se demitir!")
                        .reply(event);
                return;
            }

            var CONTRACT = TablesKt.getCONTRACT();
            user.setContractid(null);
            user.update();
            dsl.update(CONTRACT)
                    .set(CONTRACT.getSTATUS(), Contractstatus.INACTIVE)
                    .set(CONTRACT.getUPDATEDAT(), LocalDateTime.now())
                    .execute();

            ComponentBuilder.ContainerBuilder.create()
                    .setEphemeral(true)
                    .withColor(Colors.SUCCESS)
                    .addText("Você se demitiu do seu emprego com sucesso!")
                    .reply(event);
        });
    }

    private void notFound(SlashCommandInteractionEvent event) {
        ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .setEphemeral(true)
                .addText("Esse comando não foi encontrado!")
                .reply(event);
    }
}
