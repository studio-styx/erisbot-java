package functions.football.handlers

import database.extensions.transactionSuspend
import dev.minn.jda.ktx.coroutines.await
import discord.extensions.jda.users.getOrRetrieveUserAsync
import discord.extensions.jda.users.getOrRetrieveUserBlocking
import dtos.football.footballData.api.fixtureResult.match.Match
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import shared.Colors
import shared.utils.DiscordTimeStyle
import shared.utils.Utils
import studio.styx.erisbot.generated.enums.Footballbettype
import studio.styx.erisbot.generated.tables.records.FootballbetRecord
import studio.styx.erisbot.generated.tables.records.FootballmatchRecord
import studio.styx.erisbot.generated.tables.references.FOOTBALLBET
import studio.styx.erisbot.generated.tables.references.FOOTBALLPLAYER
import studio.styx.erisbot.generated.tables.references.FOOTBALLTEAM
import studio.styx.erisbot.generated.tables.references.MAILS
import studio.styx.erisbot.generated.tables.references.USER
import utils.ComponentBuilder
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.floor

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

    private val teamPointsDistributions = TeamPointsDistributions()

    suspend fun handle() {
        val score = match.score?.fullTime ?: return
        if (score.home == null || score.away == null) return

        dsl.transactionSuspend { config ->
            val tx = config.dsl()

            // Processar Pontos de Times e Jogadores
            val (homeWin, awayWin) = processPoints(tx)

            // Processar Apostas
            processBets(tx, homeWin, awayWin)
        }
    }

    private fun processPoints(tx: DSLContext): Pair<Boolean, Boolean> {
        val homeGoals = match.score!!.fullTime.home
        val awayGoals = match.score.fullTime.away

        val homeWin = homeGoals!! > awayGoals!!
        val awayWin = awayGoals > homeGoals
        val draw = homeGoals == awayGoals

        var homeTeamPoints = when {
            homeWin -> teamPointsDistributions.win
            draw -> teamPointsDistributions.draw
            else -> -teamPointsDistributions.loss
        }

        var awayTeamPoints = when {
            awayWin -> teamPointsDistributions.win
            draw -> teamPointsDistributions.draw
            else -> -teamPointsDistributions.loss
        }

        if (homeGoals - awayGoals > 4) {
            homeTeamPoints += teamPointsDistributions.manyGoalsScored
            awayTeamPoints -= teamPointsDistributions.manyGoalsSuffered
        }

        tx.update(FOOTBALLTEAM)
            .set(FOOTBALLTEAM.POINTS, FOOTBALLTEAM.POINTS.plus(homeTeamPoints))
            .where(FOOTBALLTEAM.ID.eq(dbMatch.hometeamid))
            .execute()

        tx.update(FOOTBALLTEAM)
            .set(FOOTBALLTEAM.POINTS, FOOTBALLTEAM.POINTS.plus(awayTeamPoints))
            .where(FOOTBALLTEAM.ID.eq(dbMatch.awayteamid))
            .execute()

        return Pair(homeWin, awayWin)
    }

    private suspend fun processBets(tx: DSLContext, homeWin: Boolean, awayWin: Boolean) {
        val bets = tx.selectFrom(FOOTBALLBET)
            .where(FOOTBALLBET.MATCHID.eq(dbMatch.id))
            .fetch()

        for (bet in bets) {
            val result = runCatching {
                calculateBetResult(bet, homeWin, awayWin)
            }.getOrNull() ?: continue

            // Cálculo do prêmio (Odds)
            val multiplier = when(bet.type) {
                Footballbettype.HOME_WIN -> dbMatch.oddshomewin ?: 1.5
                Footballbettype.AWAY_WIN -> dbMatch.oddsawaywin ?: 1.5
                Footballbettype.DRAW -> dbMatch.oddsdraw ?: 2.0
                else -> 3.5 // Placar exato costuma ser alto
            }

            val winAmount = if (result.won) bet.amount!!.multiply(BigDecimal(multiplier)) else BigDecimal.ZERO

            // Atualizar Usuário e Criar Mail
            val userRecord = if (result.won) {
                tx.update(USER)
                    .set(USER.MONEY, USER.MONEY.add(winAmount))
                    .where(USER.ID.eq(bet.userid))
                    .returning().fetchOne()
            } else {
                tx.selectFrom(USER).where(USER.ID.eq(bet.userid)).fetchOne()
            }

            val mailContent = buildMailContent(bet, result, winAmount)

            tx.insertInto(MAILS)
                .set(MAILS.CONTENT, mailContent)
                .set(MAILS.TAGS, result.tags.toTypedArray())
                .set(MAILS.USERID, bet.userid)
                .set(MAILS.CREATEDAT, LocalDateTime.now())
                .execute()

            // Notificação Discord (Fora da transação crítica do DB)
            if (userRecord?.dmnotification == true) {
                sendDiscordNotification(userRecord.id!!, mailContent)
            }
        }
    }

    private fun buildMailContent(bet: FootballbetRecord, result: BetOutcome, winAmount: BigDecimal): String {
        val baseContent = mutableListOf(
            "## Resultado ${if (result.won) "positivo" else "negativo"} da aposta",
            "Jogo: **${match.homeTeam.name} ${match.score?.fullTime?.home ?: "?"} x ${match.score?.fullTime?.away ?: "?"} ${match.awayTeam.name}**",
            "Competição: **${match.competition.name}**",
            "Você apostou: **${bet.amount!!.toDouble()}**",
            "Em: ${bet.type}: ${bet.quantity ?: ""}",
            result.message
        )

        if (result.won)
            baseContent.add("Você ganhou: **${winAmount.toDouble()}**!")
        else
            baseContent.add("Você perdeu a aposta.")

        return Utils.brBuilder(baseContent)
    }

    private fun calculateBetResult(bet: FootballbetRecord, homeWin: Boolean, awayWin: Boolean): BetOutcome? {
        var won = false
        var msg = ""
        val tags = mutableListOf("football", "bet")

        when(bet.type) {
            Footballbettype.HOME_WIN -> {
                won = homeWin
                tags.add("homeWin")
                msg = "Você apostou na vitória do **${match.homeTeam.name} ${if (won)
                    "e ganhou!"
                else
                    "mas o time não venceu."
                }"
            }
            Footballbettype.AWAY_WIN -> {
                won = awayWin
                tags.add("awayWin")
                msg = "Você apostou na vitória do **${match.awayTeam.name} ${if (won)
                    "e ganhou!"
                else
                    "mas o time não venceu."
                }"
            }
            Footballbettype.DRAW -> {
                won = !homeWin && !awayWin
                tags.add("draw")
                msg = "Você apostou no empate ${if (won)
                    "e acertou!"
                else
                    "mas o jogo teve um vencedor."
                }"
            }
            Footballbettype.EXACT_GOALS -> {
                // Normaliza formatos: "0-1", "0:1", "0 1" → [0, 1]
                val (hStr, aStr) = "${bet.quantity}"
                    .replace("\\s".toRegex(), "-")
                    .replace("/:/g", "-")
                    .trim()
                    .split("-")

                val h = hStr.toInt()
                val a = aStr.toInt()

                won = h == dbMatch.goalshome && a == dbMatch.goalsaway
                tags.add("exactGoals")
                msg = if (won) {
                    "Você acertou o placar exato: **$h x $a**!"
                } else {
                    "Você apostou no placar **$h x $a**, mas o resultado foi **${dbMatch.goalshome} x ${dbMatch.goalsaway}**."
                }
            }

            Footballbettype.GOALS_HOME -> {
                val goals = bet.quantity!!.toDouble()

                won = if (goals >= 0) {
                    dbMatch.goalshome!! > floor(goals)
                } else {
                    dbMatch.goalshome!! < floor(goals.unaryMinus())
                }

                tags.add("goalsHome")
                val overUnder = if (goals >= 0) "mais de" else "menos de"
                val displayValue = if (goals >= 0) goals else abs(goals)
                msg = if (won)
                    "Você acertou: **${match.homeTeam.name}** marcou $overUnder **$displayValue** gol(s)."
                else
                    "Você errou: **${match.homeTeam.name}** marcou **${dbMatch.goalshome}** gol(s). e não $overUnder **$displayValue** gol(s)"
            }

            Footballbettype.GOALS_AWAY -> {
                val goals = bet.quantity!!.toDouble()

                won = if (goals >= 0) {
                    dbMatch.goalsaway!! > floor(goals)
                } else {
                    dbMatch.goalsaway!! < floor(goals.unaryMinus())
                }

                tags.add("goalsAway")
                val overUnder = if (goals >= 0) "mais de" else "menos de"
                val displayValue = if (goals >= 0) goals else abs(goals)
                msg = if (won)
                    "Você acertou: **${match.awayTeam.name}** marcou $overUnder **$displayValue** gol(s)."
                else
                    "Você errou: **${match.awayTeam.name}** marcou **${dbMatch.goalsaway}** gol(s). e não $overUnder **$displayValue** gol(s)"
            }

            else -> return null
        }

        return BetOutcome(won, msg, tags)
    }

    private suspend fun sendDiscordNotification(userId: String, content: String) {
        runCatching {
            val user = jda.getOrRetrieveUserAsync(userId)
            val channel = user.openPrivateChannel().await()
            channel.sendMessageComponents(
                ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.FUCHSIA)
                    .addText("# Nova carta recebida!")
                    .addText(content)
                    .build()
            ).useComponentsV2().await()
        }
    }

    private data class BetOutcome(val won: Boolean, val message: String, val tags: List<String>)
}