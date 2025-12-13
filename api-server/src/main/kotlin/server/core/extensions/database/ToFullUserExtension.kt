package server.core.extensions.database

import org.jooq.Record
import server.routes.web.protectedWeb.auth.FullUserResponse
import studio.styx.erisbot.generated.tables.references.USER

fun Record.toFullUserResponse(): FullUserResponse {
    return FullUserResponse(
        id = this[USER.ID]!!,
        // Converte BigDecimal do jOOQ para Double
        money = this[USER.MONEY]?.toDouble() ?: 0.0,
        xp = this[USER.XP] ?: 0,
        contractId = this[USER.CONTRACTID],
        afkReasson = this[USER.AFKREASSON],
        // Converte LocalDateTime para String
        afkTime = this[USER.AFKTIME]?.toString(),
        dmNotification = this[USER.DMNOTIFICATION] ?: false,
        activePetId = this[USER.ACTIVEPETID],
        blacklist = this[USER.BLACKLIST]?.toString(),
        createdAt = this[USER.CREATEDAT]?.toString() ?: "",
        updatedAt = this[USER.UPDATEDAT]?.toString() ?: "",
        showNameInPresence = this[USER.SHOWNAMEINPRESENCE] ?: false,
        gender = this[USER.GENDER]?.toString(),
        readFootballBetTerms = this[USER.READFOOTBALLBETTERMS] ?: false,
        acceptedFootballTermsAt = this[USER.ACCEPTEDFOOTBALLTERMSAT]?.toString(),
        favoriteTeamId = this[USER.FAVORITETEAMID]
    )
}