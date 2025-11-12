package studio.styx.erisbot.core;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CommandManager {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private List<CommandInterface> commands; // todos os @Component commands

    // MÉTODO PÚBLICO PARA INJETAR DSL DEPOIS
    public void injectDslIntoCommands() {
        for (CommandInterface cmd : commands) {
            if (cmd instanceof AbstractCommand abstractCmd) {
                abstractCmd.setDsl(dsl);
                System.out.println("DSL injetado em: " + cmd.getClass().getSimpleName());
            }
        }
    }
}