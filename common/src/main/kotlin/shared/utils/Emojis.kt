package shared.utils

import emojis.EmojiLoader

object Icon {

    // Regex para identificar padrões (equivalente ao TS)
    private val DIGIT_REGEX = Regex("^\\d+$")
    private val URL_REGEX = Regex("/emojis/(\\d+)\\.\\w+$")
    // O regex de emoji unicode simples não é estritamente necessário se
    // assumirmos que tudo que não é ID ou URL é texto/unicode.

    /**
     * Formata o ID no padrão do Discord: <a:_:id> ou <:_:id>
     * O nome é fixado como "_" conforme solicitado.
     */
    private fun formatDiscordTag(id: String, animated: Boolean): String {
        val prefix = if (animated) "a" else ""
        return "<$prefix:_:$id>"
    }

    /**
     * Processa a string bruta (raw) do JSON e retorna a tag formatada ou o emoji original.
     */
    private fun processEmoji(input: String?, animated: Boolean): String {
        if (input.isNullOrBlank()) return "null" // Retorno padrão caso não exista

        // 1. Caso seja apenas o ID numérico (ex: "123456789")
        if (input.matches(DIGIT_REGEX)) {
            return formatDiscordTag(input, animated)
        }

        // 2. Caso seja uma URL do CDN do Discord (ex: "https://.../emojis/123.png")
        val urlMatch = URL_REGEX.find(input)
        if (urlMatch != null) {
            val id = urlMatch.groupValues[1] // Pega o grupo de captura do ID
            return formatDiscordTag(id, animated)
        }

        // 3. Caso contrário (Emoji Unicode ou texto), retorna como está
        return input
    }

    // Objeto para emojis estáticos
    object static {
        fun get(key: String): String {
            val rawValue = EmojiLoader.emojis.static[key]
            return processEmoji(rawValue, animated = false)
        }
    }

    // Objeto para emojis animados (agora com função get para garantir formatação)
    object animated {
        fun get(key: String): String {
            val rawValue = EmojiLoader.emojis.animated[key]
            return processEmoji(rawValue, animated = true)
        }
    }
}