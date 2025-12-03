package studio.styx.erisbot.core.exceptions

import net.dv8tion.jda.api.interactions.DiscordLocale
import shared.utils.Icon
import kotlin.random.Random

class InteractionUsedByUnauthorizedUserException(
    val expectedUserId: String? = null,
    val actualUserId: String? = null,
    val interactionType: String? = null,
    val language: DiscordLocale = DiscordLocale.PORTUGUESE_BRAZILIAN,
    cause: Throwable? = null
) : RuntimeException(
    buildErrorMessage(expectedUserId, actualUserId, interactionType, language),
    cause
) {

    companion object {

        private fun buildErrorMessage(
            expectedUserId: String?,
            actualUserId: String?,
            interactionType: String?,
            language: DiscordLocale
        ): String {

            val base = baseMessages(language)
            val extra = extraMessages(language)

            val pool = if (expectedUserId != null) base + extra else base

            val selected = pool.random()

            // interpolação simples
            val finalMessage = selected
                .replace("{expected}", expectedUserId ?: "?")
                .replace("{actual}", actualUserId ?: "?")
                .replace("{type}", interactionType ?: "?")

            return "${Icon.static.get("denied")} | $finalMessage"
        }

        // 🔹 Mensagens base por idioma
        private fun baseMessages(language: DiscordLocale): List<String> =
            when (language) {

                DiscordLocale.PORTUGUESE_BRAZILIAN -> listOf(
                    "Você não tem permissão para usar esta interação.",
                    "Apenas o usuário autorizado pode usar isso.",
                    "Esta ação não está disponível para você.",
                    "Não é você quem deveria usar esta interação.",
                    "Permissão negada para este comando."
                )

                DiscordLocale.ENGLISH_US -> listOf(
                    "You are not allowed to use this interaction.",
                    "Only the authorized user can use this.",
                    "This action is not available to you.",
                    "You're not the one expected to use this interaction.",
                    "Permission denied for this command."
                )

                DiscordLocale.SPANISH -> listOf(
                    "No tienes permiso para usar esta interacción.",
                    "Solo el usuario autorizado puede usar esto.",
                    "Esta acción no está disponible para ti.",
                    "No eres la persona esperada para usar esta interacción.",
                    "Permiso denegado para este comando."
                )

                else -> baseMessages(DiscordLocale.ENGLISH_US) // fallback
            }

        // 🔹 Mensagens adicionais quando o expectedUserId é fornecido
        private fun extraMessages(language: DiscordLocale): List<String> =
            when (language) {

                DiscordLocale.PORTUGUESE_BRAZILIAN -> listOf(
                    "Somente {expected} pode usar isso agora.",
                    "Esta interação foi iniciada por {expected}, então apenas ele(a) pode continuar.",
                    "{actual}, você não é o usuário esperado ({expected}).",
                    "Essa ação pertence ao usuário {expected}.",
                    "Apenas {expected} está autorizado para esta etapa."
                )

                DiscordLocale.ENGLISH_US -> listOf(
                    "Only {expected} may use this right now.",
                    "This interaction was started by {expected}, so only they may continue.",
                    "{actual}, you are not the expected user ({expected}).",
                    "This action belongs to user {expected}.",
                    "Only {expected} is authorized for this step."
                )

                DiscordLocale.SPANISH -> listOf(
                    "Solo {expected} puede usar esto ahora.",
                    "Esta interacción fue iniciada por {expected}, así que solo él/ella puede continuar.",
                    "{actual}, no eres el usuario esperado ({expected}).",
                    "Esta acción pertenece al usuario {expected}.",
                    "Solo {expected} está autorizado para este paso."
                )

                else -> extraMessages(DiscordLocale.ENGLISH_US)
            }
    }
}
