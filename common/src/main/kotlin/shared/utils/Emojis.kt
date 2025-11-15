package shared.utils

import emojis.EmojiLoader

object Icon {
    object static {
        @JvmStatic
        fun get(key: String): String {
            return EmojiLoader.emojis.static.get(key) ?: "null"
        }
    }
    val animated by lazy { EmojiLoader.emojis.animated }
}