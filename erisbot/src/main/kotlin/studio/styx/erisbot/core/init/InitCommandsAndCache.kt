package studio.styx.erisbot.core.init

import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.Command
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import shared.Cache
import studio.styx.erisbot.generated.tables.records.CommandRecord
import studio.styx.erisbot.generated.tables.references.COMMAND

@Component
class InitCommandsAndCache() {
    private var registredCommands = mutableListOf<CommandRecord>()

    fun registerCommandsInDb(tx: DSLContext, discordCommands: List<Command>) {
        for (command in discordCommands) {
            val commandName = command.name
            val ptbrCommandName = command.nameLocalizations.get(DiscordLocale.PORTUGUESE_BRAZILIAN)

            if (command.subcommandGroups.isNotEmpty()) {
                for (subgroup in command.subcommandGroups) {
                    val subgroupName = subgroup.name
                    val ptbrSubgroupName = subgroup.nameLocalizations.get(DiscordLocale.PORTUGUESE_BRAZILIAN)
                    if (subgroup.subcommands.isNotEmpty()) {
                        for (subcommand in subgroup.subcommands) {
                            try {
                                val subcommandName = subcommand.name
                                val ptbrSubcommandName = subcommand.nameLocalizations.get(DiscordLocale.PORTUGUESE_BRAZILIAN)

                                val dbCommand = tx.selectFrom(COMMAND)
                                    .where(COMMAND.NAME.eq(commandName))
                                    .and(COMMAND.SUBCOMMANDGROUP.eq(subgroupName))
                                    .and(COMMAND.SUBCOMMAND.eq(subcommandName))
                                    .fetchOne()

                                if (dbCommand == null) {
                                    val registredCommand = tx.insertInto(COMMAND)
                                        .set(COMMAND.NAME, commandName)
                                        .set(COMMAND.PTBRNAME, ptbrCommandName)
                                        .set(COMMAND.SUBCOMMANDGROUP, subgroupName)
                                        .set(COMMAND.SUBCOMMANDGROUPPTBR, ptbrSubgroupName)
                                        .set(COMMAND.SUBCOMMAND, subcommandName)
                                        .set(COMMAND.SUBCOMMANDPTBR, ptbrSubcommandName)
                                        .set(COMMAND.DISCORDID, command.id)
                                        .set(COMMAND.DESCRIPTION, subcommand.description)
                                        .set(COMMAND.DESCRIPTIONPTBR, subcommand.descriptionLocalizations.get(DiscordLocale.PORTUGUESE_BRAZILIAN))
                                        .set(COMMAND.CATEGORY, "unknown")
                                        .returning()
                                        .fetchOne()!!

                                    println("Registrado na db o novo comando: $commandName $subgroupName $subcommandName")

                                    registredCommands.add(registredCommand)
                                } else {
                                    registredCommands.add(dbCommand)
                                }
                            } catch (e: Exception) {
                                println("Erro ao registrar o subcomando: ${subcommand.name} do grupo: ${subgroup.name} do comando: ${command.name}")
                                e.printStackTrace()
                            }
                        }
                    }
                }
                continue
            }

            if (command.subcommands.isNotEmpty()) {
                for (subcommand in command.subcommands) {
                    try {
                        val subcommandName = subcommand.name
                        val ptbrSubcommandName = subcommand.nameLocalizations.get(DiscordLocale.PORTUGUESE_BRAZILIAN)

                        val dbCommand = tx.selectFrom(COMMAND)
                            .where(COMMAND.NAME.eq(commandName))
                            .and(COMMAND.SUBCOMMAND.eq(subcommandName))
                            .fetchOne()

                        if (dbCommand == null) {
                            val registredCommand = tx.insertInto(COMMAND)
                                .set(COMMAND.NAME, commandName)
                                .set(COMMAND.PTBRNAME, ptbrCommandName)
                                .set(COMMAND.SUBCOMMAND, subcommandName)
                                .set(COMMAND.SUBCOMMANDPTBR, ptbrSubcommandName)
                                .set(COMMAND.DISCORDID, command.id)
                                .set(COMMAND.DESCRIPTION, subcommand.description)
                                .set(COMMAND.DESCRIPTIONPTBR, subcommand.descriptionLocalizations.get(DiscordLocale.PORTUGUESE_BRAZILIAN))
                                .set(COMMAND.CATEGORY, "unknown")
                                .returning()
                                .fetchOne()!!

                            println("Registrado na db o novo comando: $commandName $subcommandName")

                            registredCommands.add(registredCommand)
                        } else {
                            registredCommands.add(dbCommand)
                        }
                    } catch (e: Exception) {
                        println("Erro ao registrar o subcomando: ${subcommand.name} do comando: ${command.name}")
                        e.printStackTrace()
                    }
                }
                continue
            }

            try {
                val dbCommand = tx.selectFrom(COMMAND)
                    .where(COMMAND.NAME.eq(commandName))
                    .fetchOne()

                if (dbCommand == null) {
                    val registredCommand = tx.insertInto(COMMAND)
                        .set(COMMAND.NAME, commandName)
                        .set(COMMAND.PTBRNAME, ptbrCommandName)
                        .set(COMMAND.DISCORDID, command.id)
                        .set(COMMAND.DESCRIPTION, command.description)
                        .set(COMMAND.DESCRIPTIONPTBR, command.descriptionLocalizations.get(DiscordLocale.PORTUGUESE_BRAZILIAN))
                        .set(COMMAND.CATEGORY, "unknown")
                        .returning()
                        .fetchOne()!!

                    println("Registrado na db o novo comando: $commandName")

                    registredCommands.add(registredCommand)
                } else {
                    registredCommands.add(dbCommand)
                }
            } catch (e: Exception) {
                println("Erro ao registrar o comando: ${command.name}")
                e.printStackTrace()
            }
        }

        Cache.set("commands", registredCommands)
    }
}