package games.tryvia.dtos

import net.dv8tion.jda.api.entities.Message

data class TryviaMessage (
    val message: Message,
    var hasDeleted: Boolean = false,
    var turnToDelete: Int = 0
)