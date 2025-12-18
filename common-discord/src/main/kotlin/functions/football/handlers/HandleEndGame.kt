package functions.football.handlers

import dtos.football.footballData.api.fixtureResult.match.Match
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import studio.styx.erisbot.generated.enums.Footballbettype
import studio.styx.erisbot.generated.tables.records.FootballmatchRecord
import studio.styx.erisbot.generated.tables.references.FOOTBALLBET
import studio.styx.erisbot.generated.tables.references.FOOTBALLPLAYER
import studio.styx.erisbot.generated.tables.references.FOOTBALLTEAM
import java.math.BigDecimal

class HandleEndGame(
    private val match: Match,
    private val dbMatch: FootballmatchRecord,
    private val dsl: DSLContext,
    private val jda: JDA
) {
    private data class TeamPointsDistributions(
        val win: Int = 5,
        val draw: Int = 3,
        val loss: Int = -2,
        val manyGoalsScored: Int = 8,
        val manyGoalsSuffered: Int = -5
    )

    private data class PlayerPointsDistributions(
        val pointsPerGoal: Int = 2,
        val pointsPerOwnGoal: Int = 3, // negativo
        val pointsPerAssist: Int = 1
    )

    private val teamPointsDistributions = TeamPointsDistributions()
    private val playerPointsDistributions = PlayerPointsDistributions()

    suspend fun handle(async: Boolean = false) {
        if (match.score?.fullTime?.home == null || match.score.fullTime.away == null) return

        val handleTeamsResult = if (async) {
            withContext(Dispatchers.IO) {
                handleTeamsPoints(false)
            }
        } else {
            handleTeamsPoints(true)
        }

        handleBets(async, handleTeamsResult.first, handleTeamsResult.second)
    }

    fun handleTeamsPoints(alreadyInTransaction: Boolean): Pair<Boolean, Boolean> {
        val homeGoals = match.score!!.fullTime.home
        val awayGoals = match.score.fullTime.away

        val homeWin = homeGoals > awayGoals
        val awayWin = awayGoals > homeGoals
        val draw = homeGoals == awayGoals

        val goals = match.goals

        if (!goals.isNullOrEmpty()) {
            for (goal in goals) {
                try {
                    if (goal.type == "OWN_GOAL") {
                        dsl.update(FOOTBALLPLAYER)
                            .set(FOOTBALLPLAYER.POINTS, FOOTBALLPLAYER.POINTS.minus(playerPointsDistributions.pointsPerOwnGoal))
                            .where(FOOTBALLPLAYER.APIID.eq(goal.scorer.id.toInt()))
                            .execute()
                    } else {
                        dsl.update(FOOTBALLPLAYER)
                            .set(FOOTBALLPLAYER.POINTS, FOOTBALLPLAYER.POINTS.plus(playerPointsDistributions.pointsPerGoal))
                            .where(FOOTBALLPLAYER.APIID.eq(goal.scorer.id.toInt()))
                            .execute()
                        if (goal.assist != null) {
                            dsl.update(FOOTBALLPLAYER)
                                .set(FOOTBALLPLAYER.POINTS, FOOTBALLPLAYER.POINTS.plus(playerPointsDistributions.pointsPerAssist))
                                .where(FOOTBALLPLAYER.APIID.eq(goal.assist.id.toInt()))
                                .execute()
                        }
                    }
                } catch (_: Exception) {}
            }
        }

         var homeTeamPoints = 0
         var awayTeamPoints = 0

         if (homeWin) {
             homeTeamPoints += teamPointsDistributions.win
             awayTeamPoints -= teamPointsDistributions.loss
         }
         if (awayWin) {
             homeTeamPoints -= teamPointsDistributions.loss
             awayTeamPoints += teamPointsDistributions.win
         }
         if (draw) {
             homeTeamPoints += teamPointsDistributions.draw
             awayTeamPoints += teamPointsDistributions.draw
         }

         // calcular se a diferença de gols é maior que 4
         val goalsDifference = homeGoals - awayGoals
         if (goalsDifference > 4) {
             homeTeamPoints += teamPointsDistributions.manyGoalsScored
             awayTeamPoints -= teamPointsDistributions.manyGoalsSuffered
         }


         if (alreadyInTransaction) {
             dsl.update(FOOTBALLTEAM)
                 .set(FOOTBALLTEAM.POINTS, FOOTBALLTEAM.POINTS.plus(homeTeamPoints))
                 .where(FOOTBALLTEAM.ID.eq(dbMatch.hometeamid!!))
                 .execute()
             dsl.update(FOOTBALLTEAM)
                 .set(FOOTBALLTEAM.POINTS, FOOTBALLTEAM.POINTS.plus(awayTeamPoints))
                 .where(FOOTBALLTEAM.ID.eq(dbMatch.awayteamid!!))
                 .execute()
         } else {
             dsl.transaction { config ->
                 val tx = config.dsl()
                 tx.update(FOOTBALLTEAM)
                     .set(FOOTBALLTEAM.POINTS, FOOTBALLTEAM.POINTS.plus(homeTeamPoints))
                     .where(FOOTBALLTEAM.ID.eq(dbMatch.hometeamid!!))
                     .execute()
                 tx.update(FOOTBALLTEAM)
                     .set(FOOTBALLTEAM.POINTS, FOOTBALLTEAM.POINTS.plus(awayTeamPoints))
                     .where(FOOTBALLTEAM.ID.eq(dbMatch.awayteamid!!))
                     .execute()
             }
         }

        return Pair(homeWin, awayWin)
    }

    suspend fun handleBets(async: Boolean = false, homeWin: Boolean, awayWin: Boolean) {
        val bets = if (async) {
            withContext(Dispatchers.IO) {
                dsl.selectFrom(FOOTBALLBET)
                    .where(FOOTBALLBET.MATCHID.eq(dbMatch.id!!))
                    .fetch()
            }
        } else {
            dsl.selectFrom(FOOTBALLBET)
                .where(FOOTBALLBET.MATCHID.eq(dbMatch.id!!))
                .fetch()
        }

        if (!bets.isEmpty()) {
            for (bet in bets) {
                try {
                    var won = false
                    var winAmount = BigDecimal(0)
                    var resultMessage = ""
                    var tags = mutableListOf("fooball", "bet")

                    when(bet.type) {
                        Footballbettype.HOME_WIN -> {
                            won = homeWin
                            tags.add("homeWin")
                            resultMessage = "Você apostou na vitória do **${match.homeTeam.name} ${if (won)
                                "e ganhou!"
                            else
                                "mas o time não venceu."
                            }"
                        }
                        Footballbettype.AWAY_WIN -> {
                            won = awayWin
                            tags.add("awayWin")
                            resultMessage = "Você apostou na vitória do **${match.awayTeam.name} ${if (won)
                                "e ganhou!"
                            else
                                "mas o time não venceu."
                            }"
                        }
                        Footballbettype.DRAW -> {
                            won = !homeWin && !awayWin
                            tags.add("draw")
                            resultMessage = "Você apostou no empate ${if (won)
                                "e acertou!"
                            else
                                "mas o jogo teve um vencedor."
                            }"
                        }
                        Footballbettype.EXACT_GOALS -> {
                            if (bet.quantity == null) return

                            // Normaliza formatos: "0-1", "0:1", "0 1" → [0, 1]
                            val (hStr, aStr) = "${bet.quantity}"
                                .replace("\\s".toRegex(), "-")
                                .replace("/:/g", "-")
                                .trim()
                                .split("-")

                            val h = hStr.toIntOrNull()
                            val a = aStr.toIntOrNull()

                            if (h == null || a == null) return

                            won = h == dbMatch.goalshome && a == dbMatch.goalsaway
                            tags.add("exactGoals")
                            resultMessage = if (won) {
                                "Você acertou o placar exato: **$h x $a**!"
                            } else {
                                "Você apostou no placar **$h x $a**, mas o resultado foi **${dbMatch.goalshome} x ${dbMatch.goalsaway}**."
                            }
                        }
                    }
                }
            }
        }
    }
}