package studio.styx.erisbot.features.commands.economy.jobs;

import database.utils.DatabaseUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shared.Colors;
import studio.styx.erisbot.core.CommandInterface;
import studio.styx.erisbot.generated.tables.records.CompanyRecord;
import studio.styx.erisbot.generated.tables.records.UserRecord;
import studio.styx.erisbot.generated.tables.references.TablesKt;
import studio.styx.erisbot.menus.economy.workSystem.JobsSearch;
import utils.ComponentBuilder;

import java.util.List;

@Component
public class JobsSearchCommand implements CommandInterface {
    @Autowired
    private DSLContext dsl;

    @Override
    public SlashCommandData getSlashCommandData() {
        return Commands.slash("job", "search for a job or get dismiss")
                .addSubcommands(
                        new SubcommandData("search", "search for a job"),
                        new SubcommandData("dismiss", "get dismiss from your job")
                );
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
            
            Integer companyId = user.getCompanyid();
            
            if (companyId == null || companyId == 0) {
                ComponentBuilder.ContainerBuilder.create()
                        .setEphemeral(true)
                        .withColor(Colors.DANGER)
                        .addText("Você não tem um emprego para se demitir!")
                        .reply(event);
                return;
            }

            // Set the company ID to null to represent unemployment
            user.setCompanyid(null);
            user.update();

            // Send a success message
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
