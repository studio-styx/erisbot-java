package database.utils

enum class LogType {
    INFO,
    DEBUG,
    WARN,
    ERROR
}

class LogsManage {
    private var userId: String? = null;
    private var message: String? = null;
    private var type: LogType? = null;
    private var level: Int = 1;
    private var tags: List<String> = listOf();

    fun setUserId(userId: String) =  apply { this.userId = userId }
    fun setMessage(message: String) = apply { this.message = message }
    fun setType(type: LogType) = apply { this.type = type }
    fun setLevel(level: Int) = apply { this.level = level }
    fun setTags(tags: List<String>) = apply { this.tags = tags }
    fun addTag(tag: String) = apply { this.tags = this.tags.plus(tag) }

    fun getUserId() = { this.userId }
    fun getMessage() = { this.message }
    fun getType() = { this.type }
    fun getLevel() = { this.level }
    fun getTags() = { this.tags }

    fun build() {

    }
}