plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "ErisBot"
include("common")
include("database")
include("games")
include("scheduler")
include("redisbridge")
include("common-discord")
include("erisbot")
include("gemini-service")