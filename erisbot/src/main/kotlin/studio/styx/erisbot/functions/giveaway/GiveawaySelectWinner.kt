package studio.styx.erisbot.functions.giveaway

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import org.jooq.Record
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.erisbot.generated.tables.references.GUILDGIVEAWAY
import studio.styx.erisbot.generated.tables.records.UsergiveawayRecord
import studio.styx.erisbot.generated.tables.references.ROLEMULTIPLEENTRY
import kotlin.math.min
import kotlin.random.Random

class GiveawaySelectWinner(
    private val jda: JDA,
    private val dsl: DSLContext,
    private val giveaway: Record,
    private val participants: List<UsergiveawayRecord>,
    private val connectedGuilds: List<Record>,
    private val roleEntries: List<Record>,
    private val quantity: Int = 0
) {

    // Classe auxiliar para segurar o participante e suas chances (entries)
    private data class Candidate(
        val userRecord: UsergiveawayRecord,
        val entries: Int
    )

    suspend fun select(): List<UsergiveawayRecord>? = coroutineScope {
        // 1. Filtrar participantes que já ganharam
        val eligibleParticipants = participants.filter { it.iswinner != true }

        if (eligibleParticipants.isEmpty()) return@coroutineScope null

        // 2. Verificar requisitos e calcular entries para cada usuário
        // Usamos async/awaitAll para fazer isso em paralelo
        val validCandidates = eligibleParticipants.map { participant ->
            async {
                val userId = participant.userid!!

                // Instancia a verificação (usando a classe otimizada anteriormente)
                val verifier = VerifyUserRequirement(jda, giveaway, userId, connectedGuilds, dsl)
                val result = verifier.verify()

                // Se houver erros (missing requirements), o usuário é desqualificado
                if (result.errors.isNotEmpty()) {
                    return@async null
                }

                // 3. Calcular Entries (Base = 1 + Extras)
                var entries = 1

                for (roleEntry in roleEntries) {
                    val roleId = roleEntry.get(ROLEMULTIPLEENTRY.ROLEID) ?: continue
                    val extraEntries = roleEntry.get(ROLEMULTIPLEENTRY.EXTRAENTRIES) ?: 0

                    // Lógica do TS: Procurar em qual servidor conectado esse cargo existe
                    // Otimização: Buscamos nos membros já cacheados pelo verifier
                    val guildIdWithRole = connectedGuilds.firstOrNull { guildRec ->
                        val guildId = guildRec.get(GUILDGIVEAWAY.GUILDID)!!
                        val guild = jda.getGuildById(guildId)
                        guild?.getRoleById(roleId) != null
                    }?.get(GUILDGIVEAWAY.GUILDID)

                    if (guildIdWithRole != null) {
                        val member = result.members[guildIdWithRole]
                        // Verifica se o membro tem o cargo (usando cache de roles do Member)
                        if (member != null && member.roles.any { it.id == roleId }) {
                            entries += extraEntries
                        }
                    }
                }

                Candidate(participant, entries)
            }
        }.awaitAll().filterNotNull()

        if (validCandidates.isEmpty()) return@coroutineScope null

        val winnersCount = if (quantity > 0) quantity else (giveaway.get(GIVEAWAY.USERSWINS) ?: 1)
        val totalWinners = min(winnersCount, validCandidates.size)

        val winners = mutableListOf<UsergiveawayRecord>()
        val availableCandidates = validCandidates.toMutableList()

        repeat(totalWinners) {
            if (availableCandidates.isEmpty()) return@repeat

            val totalEntries = availableCandidates.sumOf { it.entries }

            if (totalEntries == 0) return@repeat

            val randomValue = Random.nextDouble() * totalEntries
            var cumulative = 0.0
            var selectedIndex = -1

            for ((index, candidate) in availableCandidates.withIndex()) {
                cumulative += candidate.entries
                if (randomValue <= cumulative) {
                    selectedIndex = index
                    break
                }
            }

            if (selectedIndex == -1) {
                selectedIndex = 0
            }

            // Adiciona aos vencedores e remove da lista de disponíveis
            winners.add(availableCandidates[selectedIndex].userRecord)
            availableCandidates.removeAt(selectedIndex)
        }

        return@coroutineScope winners.ifEmpty { null }
    }
}