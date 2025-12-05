package studio.styx.erisbot.functions.giveaway

import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import org.jooq.DSLContext
import org.jooq.Record
import studio.styx.erisbot.generated.tables.references.GIVEAWAY
import studio.styx.erisbot.generated.tables.references.GUILDGIVEAWAY
import studio.styx.erisbot.generated.tables.references.GUILDMEMBER
import studio.styx.erisbot.generated.tables.references.GUILDSETTINGS

// Data class para retornar o resultado de forma limpa
data class VerificationResult(
    val errors: List<String>,
    val members: Map<String, Member?>
)

class VerifyUserRequirement(
    private val jda: JDA,
    private val giveaway: Record,
    private val userId: String,
    connectedGuilds: List<Record>,
    private val dsl: DSLContext
) {
    private val giveawayId = giveaway.get(GIVEAWAY.ID)

    private val targetGuilds = connectedGuilds.filter {
        it.get(GUILDGIVEAWAY.GIVEAWAYID) == giveawayId
    }

    private suspend fun fetchMembersMap(): Map<String, Member?> = coroutineScope {
        targetGuilds.map { record ->
            async {
                val guildId = record.get(GUILDGIVEAWAY.GUILDID)!!
                val guild = jda.getGuildById(guildId)

                // Tenta cache primeiro, depois API
                val member = guild?.getMemberById(userId)
                    ?: try { guild?.retrieveMemberById(userId)?.await() } catch (_: Exception) { null }

                guildId to member
            }
        }.awaitAll().toMap()
    }

    private fun checkBlacklistedRoles(members: Map<String, Member?>): List<String> {
        val errors = mutableListOf<String>()

        for (guildRecord in targetGuilds) {
            val guildId = guildRecord.get(GUILDGIVEAWAY.GUILDID)!!
            val member = members[guildId] ?: continue // Se não está no servidor, ignorar check de cargo

            val blacklistedIds = guildRecord.get(GUILDGIVEAWAY.BLACKLISTROLES)?.filterNotNull() ?: continue
            if (blacklistedIds.isEmpty()) continue

            // Otimização: Interseção de Sets é mais rápida que find aninhado
            val memberRoleIds = member.roles.map { it.id }.toSet()
            val forbiddenRole = blacklistedIds.firstOrNull { it in memberRoleIds }

            if (forbiddenRole != null) {
                // Pega o nome do cargo direto do objeto Member se possível, ou busca o nome
                val roleName = member.roles.find { it.id == forbiddenRole }?.name ?: "Desconhecido"
                errors.add("Você possui o cargo proibido **`$roleName`** no servidor **`${member.guild.name}`**!")
            }
        }
        return errors
    }

    private fun checkXpRequirements(members: Map<String, Member?>): List<String> {
        // 1. Filtrar quais guilds têm requisito de XP e sistema ativado
        val xpGuilds = targetGuilds.filter { g ->
            g.get(GUILDGIVEAWAY.XPREQUIRED) != null &&
                    g.get(GUILDSETTINGS.XPSYSTEMENABLED) == true
        }

        if (xpGuilds.isEmpty()) return emptyList()

        val guildIdsToCheck = xpGuilds.map { it.get(GUILDGIVEAWAY.GUILDID)!! }

        // 2. Única Query (Batch)
        val userXpMap = dsl.select(GUILDMEMBER.GUILDID, GUILDMEMBER.XP)
            .from(GUILDMEMBER)
            .where(
                GUILDMEMBER.ID.eq(userId)
                    .and(GUILDMEMBER.GUILDID.`in`(guildIdsToCheck))
            )
            .fetchMap(GUILDMEMBER.GUILDID, GUILDMEMBER.XP)

        val errors = mutableListOf<String>()

        // 3. Validação em memória
        for (guildRecord in xpGuilds) {
            val guildId = guildRecord.get(GUILDGIVEAWAY.GUILDID)!!
            val member = members[guildId] ?: continue // Se não é membro, o erro de "not in server" já vai cobrir

            val requiredXp = guildRecord.get(GUILDGIVEAWAY.XPREQUIRED)!!
            val currentXp = (userXpMap[guildId] ?: 0).toLong()

            if (currentXp < requiredXp) {
                // Usa o nome da guild direto do objeto membro cacheado
                val guildName = member.guild.name
                errors.add("Você precisa de **`$requiredXp`** XP no servidor **`$guildName`** para participar!")
            }
        }

        return errors
    }

    suspend fun verify(): VerificationResult {
        val membersMap = fetchMembersMap()
        val errors = mutableListOf<String>()
        val mustStay = giveaway.get(GIVEAWAY.SERVERSTAYREQUIRED) == true

        // 1. Check: Presença no Servidor
        targetGuilds.forEach { record ->
            val guildId = record.get(GUILDGIVEAWAY.GUILDID)!!
            val member = membersMap[guildId]

            if (member == null && mustStay) {
                // Tenta pegar nome do JDA cache, senão usa ID
                val guildName = jda.getGuildById(guildId)?.name ?: guildId
                errors.add("Você precisa estar no servidor **`$guildName`**")
            }
        }

        // 2. Check: Cargos Proibidos
        errors.addAll(checkBlacklistedRoles(membersMap))

        // 3. Check: XP
        errors.addAll(checkXpRequirements(membersMap))

        return VerificationResult(errors, membersMap)
    }
}