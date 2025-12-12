package discord.extensions.jda.users

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User

suspend fun JDA.getOrRetrieveUserAsync(userId: String): User {
    return this.getUserById(userId) ?: run {
        this.retrieveUserById(userId).await()
    }
}

suspend fun JDA.getOrRetrieveUserOrNullAsync(userId: String): User? {
    return this.getUserById(userId) ?: runCatching {
        this.retrieveUserById(userId).await()
    }.getOrNull()
}

fun JDA.getOrRetrieveUserBlockingOrNull(userId: String): User {
    return this.getUserById(userId) ?: this.retrieveUserById(userId).complete()
}

fun JDA.safeGetOrRetrieveUserBlockingOrNull(userId: String): User? {
    return this.getUserById(userId) ?: runCatching {
        this.retrieveUserById(userId).complete()
    }.getOrNull()
}