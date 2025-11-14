package shared.utils

object MentionUtil {
    fun userMention(id: String): String {
        return "<@$id>"
    }

    fun roleMention(id: String): String {
        return "<@&$id>"
    }

    fun channelMention(id: String): String {
        return "<#$id>"
    }

    fun commandMention(id: String, name: String): String {
        return "<$id:$name>"
    }
}