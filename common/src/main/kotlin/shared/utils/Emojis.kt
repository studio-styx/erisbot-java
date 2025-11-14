package shared.utils

import emojis.EmojiLoader

object Icon {
    val static by lazy { EmojiLoader.emojis.static }
    val animated by lazy { EmojiLoader.emojis.animated }
}