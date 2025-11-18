package studio.styx.erisbot.menus.economy.workSystem;

import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import shared.Colors;
import shared.utils.Utils;
import studio.styx.erisbot.generated.tables.records.CompanyRecord;
import utils.ComponentBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobsSearch {
    public List<MessageTopLevelComponent> jobsContainer(
            String userId,
            List<CompanyRecord> companys,
            int page
    ) {
        ComponentBuilder.ContainerBuilder container = ComponentBuilder.ContainerBuilder.create().withColor(Colors.DANGER);

        container.addText("## Centro de empregos\nVerifique abaixo os empregos disponiveis");
        container.addDivider(false);

        int jobsPerPage = 6;
        int startIndex = (page - 1) * jobsPerPage;
        int endIndex = Math.min(startIndex + jobsPerPage, companys.size());

        List<CompanyRecord> pageCompanys;

        if (startIndex >= companys.size()) {
            pageCompanys = List.of();
        } else {
            pageCompanys = companys.subList(startIndex, endIndex);
        }

        for (var company : pageCompanys) {
            int index = pageCompanys.indexOf(company);
            Map<String, String> replacements = new HashMap<>();
            replacements.put("index", String.valueOf((page - 1) * jobsPerPage + index + 1));
            replacements.put("companyName", String.valueOf(Utils.alternate(company.getName(), "Desconhecido")));
            replacements.put("difficulty", String.valueOf(Utils.alternate(company.getDifficulty(), "desconhecido")));
            replacements.put("description", String.valueOf(Utils.alternate(company.getDescription(), "Sem descrição")));
            replacements.put("salary", Utils.formatNumber(company.getWage().doubleValue()));
            replacements.put("xp", Utils.formatNumber(company.getExperience().doubleValue()));

            container.addSection(
                    Button.primary("jobs/interview/" + company.getId() + "/" + userId, "Participar da Entrevista"),
                        Utils.replaceText(
                                Utils.brBuilder(
                                        "{index}. **{companyName}**",
                                        "> **Dificuldade**: {difficulty}",
                                        "> **Descrição**: {description}",
                                        "> **Xp necessário**: {xp}",
                                        "> **Salário**: {salary}"
                                ),
                                replacements
                        )
            );
            if (index < pageCompanys.size() - 1) {
                container.addDivider(false);
            }
        }

        int totalPages = (int) Math.ceil((double) companys.size() / jobsPerPage);
        boolean firstPage = page <= 1;
        boolean lastPage = page >= totalPages;

        ActionRow buttons = ActionRow.of(
                Button.of(
                        firstPage ? ButtonStyle.SECONDARY : ButtonStyle.PRIMARY,
                        "jobs/menu/page/" + (page - 1) + "/" + userId,
                        "Voltar"
                ).withDisabled(firstPage),
                Button.of(
                        lastPage ? ButtonStyle.SECONDARY : ButtonStyle.PRIMARY,
                        "jobs/menu/page/" + (page + 1) + "/" + userId,
                        "Avançar"
                ).withDisabled(lastPage)
        );

        return List.of(container.build(), buttons);
    }
}