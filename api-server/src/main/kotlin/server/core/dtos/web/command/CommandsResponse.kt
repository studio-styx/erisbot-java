package server.core.dtos.web.command

import kotlinx.serialization.Serializable

@Serializable
data class CommandsResponse(
    val id: Int,
    val name: CommandName,
    val subCommandGroup: CommandSubCommandGroupName?,
    val subCommand: CommandSubCommandName?,
    val description: CommandDescription,
    val isEnabled: Boolean,
    val category: String,
    val explanation: kotlinx.serialization.json.JsonElement
)

@Serializable
data class CommandName(
    val name: String,
    val ptbrName: String?
)

@Serializable
data class CommandSubCommandGroupName(
    val name: String,
    val ptbrName: String?
)

@Serializable
data class CommandSubCommandName(
    val name: String,
    val ptbrName: String?
)

@Serializable
data class CommandDescription(
    val description: String,
    val ptbrDescription: String?
)