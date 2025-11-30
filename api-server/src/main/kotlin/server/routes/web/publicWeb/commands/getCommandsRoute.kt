package server.routes.web.publicWeb.commands

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.jooq.DSLContext
import server.core.dtos.web.command.CommandDescription
import server.core.dtos.web.command.CommandName
import server.core.dtos.web.command.CommandSubCommandGroupName
import server.core.dtos.web.command.CommandSubCommandName
import server.core.dtos.web.command.CommandsResponse
import studio.styx.erisbot.generated.tables.references.COMMAND

fun Route.getCommandsRoute(dsl: DSLContext) {
    get("/app/commands") {
        val records = dsl.selectFrom(COMMAND).fetch()
        val list = records.map { rec ->
            val jsonb = rec.get(COMMAND.EXPLANATION)
            val explanationJson: JsonElement = jsonb
                ?.data()
                ?.let { Json.parseToJsonElement(it) }
                ?: JsonNull

            val commandId = rec.get(COMMAND.ID)!!
            val commandName = rec.get(COMMAND.NAME)!!
            val commandPtbrName = rec.get(COMMAND.PTBRNAME)
            val subCommandGroup = rec.get(COMMAND.SUBCOMMANDGROUP)
            val subCommandGroupPtbr = rec.get(COMMAND.SUBCOMMANDGROUPPTBR)
            val subCommand = rec.get(COMMAND.SUBCOMMAND)
            val subCommandPtbr = rec.get(COMMAND.SUBCOMMANDPTBR)

            CommandsResponse(
                id = commandId,
                name = CommandName(
                    name = commandName,
                    ptbrName = commandPtbrName
                ),
                subCommandGroup = if (subCommandGroup != null) CommandSubCommandGroupName(
                    name = subCommandGroup,
                    ptbrName = subCommandGroupPtbr
                ) else null,
                subCommand = if (subCommand != null) CommandSubCommandName(
                    name = subCommand,
                    ptbrName = subCommandPtbr
                ) else null,
                description = CommandDescription(
                    description = rec.get(COMMAND.DESCRIPTION)!!,
                    ptbrDescription = rec.get(COMMAND.DESCRIPTIONPTBR)
                ),
                category = rec.get(COMMAND.CATEGORY)!!,
                isEnabled = rec.get(COMMAND.ISENABLED) ?: true,
                explanation = explanationJson
            )
        }
        call.respond(list)
    }
}