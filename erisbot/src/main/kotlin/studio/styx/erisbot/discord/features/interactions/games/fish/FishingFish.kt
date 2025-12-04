package studio.styx.erisbot.discord.features.interactions.games.fish

import database.utils.LogManage
import dev.minn.jda.ktx.coroutines.await
import games.fish.fishingMenu
import games.fish.setFishTimeout
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.jooq.DSLContext
import org.jooq.impl.DSL.rand
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import shared.Cache
import shared.Colors
import shared.utils.CustomIdHelper
import shared.utils.Icon
import shared.utils.Utils
import studio.styx.erisbot.core.exceptions.InteractionUsedByUnauthorizedUserException
import studio.styx.erisbot.core.extensions.jda.reply.rapidContainerReply
import studio.styx.erisbot.core.interfaces.ResponderInterface
import studio.styx.erisbot.generated.enums.Rarity
import studio.styx.erisbot.generated.tables.records.UserfishingrodRecord
import studio.styx.erisbot.generated.tables.references.FISH
import studio.styx.erisbot.generated.tables.references.FISHINGROD
import studio.styx.erisbot.generated.tables.references.USERFISH
import studio.styx.erisbot.generated.tables.references.USERFISHINGROD
import utils.ComponentBuilder
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.round
import kotlin.random.Random

@Component
class FishingFish : ResponderInterface {
    @Autowired
    lateinit var dsl: DSLContext

    override val customId = "fishing/fish/:button/:userId/:createdAt/:isCorrect"

    override suspend fun execute(event: ButtonInteractionEvent) {
        val params = CustomIdHelper(customId, event.customId)

        val userId = params.get("userId")
        val createdAt = params.get("createdAt")!!.toLong()
        val isCorrect = params.get("isCorrect") == "true"

        if (event.user.id != userId) throw InteractionUsedByUnauthorizedUserException(userId)

        val messageContainer = event.message.components[0].asContainer()
        val textDisplay = messageContainer.components[0].asTextDisplay()

        val rodId = textDisplay.uniqueId
        val round = textDisplay.content.split("| ")[1].toInt()

        val correctButton = messageContainer.components.last().asActionRow().buttons.find {
            it.style == ButtonStyle.SUCCESS
        }?.customId?.split("/")[2]?.toInt()

        val key = "fishing:fishs:$userId"
        var avaibleFishes = Cache.get<Int>(key) ?: run {
            Cache.set(key, 20, 20, TimeUnit.MINUTES)
            20
        }

        if (avaibleFishes < 1) {
            event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("denied")} | Todos os peixes foram pescados! Espere um pouco para tentar novamente!")
            return
        }

        event.deferEdit().await()
        event.editComponents(fishingMenu(userId, rodId, round, correctButton, true))

        if (!isCorrect) {
            val fishingRod = dsl.transactionResult { config ->
                val tx = config.dsl()

                // Tenta atualizar
                val updatedRows = tx.update(USERFISHINGROD)
                    .set(USERFISHINGROD.DURABILITY, USERFISHINGROD.DURABILITY.minus(1))
                    .where(USERFISHINGROD.USERID.eq(userId))
                    .and(USERFISHINGROD.ID.eq(rodId))
                    .execute()

                // Se nenhuma linha foi atualizada, retorna null
                if (updatedRows == 0) {
                    return@transactionResult null
                }

                // Busca o registro atualizado
                tx.selectFrom(USERFISHINGROD)
                    .where(USERFISHINGROD.USERID.eq(userId))
                    .and(USERFISHINGROD.ID.eq(rodId))
                    .fetchOneInto(UserfishingrodRecord::class.java)
            }

            if (fishingRod == null) {
                event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("Eris_cry")} | Parece que sua vara de pesca sumiu derrepente enquanto pescava!")
                return
            }

            if (fishingRod.durability!! < 1) {
                dsl.deleteFrom(USERFISHINGROD)
                    .where(USERFISHINGROD.ID.eq(rodId))
                    .and(USERFISHINGROD.USERID.eq(userId))
                    .execute()
                event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("Eris_cry")} | Sua vara de pesca quebrou após selecionar o local errado do lago!")
                return
            }

            event.hook.sendMessageComponents(ComponentBuilder.ContainerBuilder.create()
                .withColor(Colors.DANGER)
                .addText("${Icon.static.get("denied")} | Você apertou em um botão errado ou antes da hora! sua vara de pesca diminuiu 1 ponto de durabilidade")
                .build()
            ).useComponentsV2().setEphemeral(true).await()
            event.hook.editOriginalComponents(fishingMenu(userId, rodId, round + 1)).useComponentsV2().await()
            setFishTimeout(event, round + 1, Utils.getRandomLong(1000, 10000), userId)
        } else {
            val time = createdAt / LocalDateTime.now().nano

            dsl.transaction { config ->
                val tx = config.dsl()
                val rarity = getFishRarity(time)

                val selectedFish = tx.selectFrom(FISH)
                    .where(FISH.RARITY.eq(rarity))
                    .orderBy(rand())
                    .limit(1)
                    .fetchOne()
                val fishingRod = tx.select(USERFISHINGROD.asterisk(), FISHINGROD.asterisk())
                    .from(USERFISHINGROD)
                    .innerJoin(FISHINGROD).on(USERFISHINGROD.FISHINGRODID.eq(FISHINGROD.ID))
                    .where(USERFISHINGROD.USERID.eq(userId))
                    .and(USERFISHINGROD.ID.eq(rodId))
                    .fetchOne()

                if (selectedFish == null) {
                    event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("error")} | Não foi possivel encontrar um peixe adequado! sinto muito por isso, isso foi erro meu! ${Icon.static.get("Eris_cry")}")
                    return@transaction
                }

                if (fishingRod == null) {
                    event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("Eris_cry")} | Você perdeu sua vara de pesca em algum lugar desse lago!")
                    return@transaction
                }

                if (fishingRod.get(USERFISHINGROD.DURABILITY)!! < 1) {
                    tx.deleteFrom(USERFISHINGROD)
                        .where(USERFISHINGROD.ID.eq(rodId))
                        .and(USERFISHINGROD.USERID.eq(userId))
                        .execute()
                    event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("Eris_cry")} | Sua vara de pesca quebrou antes de conseguir pescar aquele peixe! era um(a) **${selectedFish.name}**")
                    return@transaction
                }

                val fishRarityOrder = mutableListOf(
                        Rarity.LEGENDARY to 2.3,
                        Rarity.EPIC to 1.7,
                        Rarity.RARE to 1.2,
                        Rarity.UNCOMUM to 0.9,
                        Rarity.COMUM to 0.7
                )

                val rodRarityOrder = mutableListOf(
                        Rarity.LEGENDARY to 2.0,
                        Rarity.EPIC to 1.6,
                        Rarity.RARE to 1.0,
                        Rarity.UNCOMUM to 0.5,
                        Rarity.COMUM to 0.3
                )

                // converte explicitamente a durabilidade para Double (jOOQ retorna tipo genérico)
                val baseDurability = fishingRod.get(FISHINGROD.DURABILITY)!!
                val baseDurabilityDouble = (baseDurability as Number).toDouble()

                val fishFactor = fishRarityOrder.find { it.first == rarity }!!.second
                val rodFactor = rodRarityOrder.find { it.first == fishingRod.get(FISHINGROD.RARITY) }!!.second

                val rawDecrement = (fishFactor / rodFactor) * (5.0 / baseDurabilityDouble)

                val valueToDecrement = max(1, round(rawDecrement).toInt())

                tx.insertInto(USERFISH)
                    .set(USERFISH.FISHID, selectedFish.id)
                    .set(USERFISH.USERID, userId)
                    .set(USERFISH.CREATEDAT, LocalDateTime.now())
                    .execute()
                val newFishingRod = tx.update(USERFISHINGROD)
                    .set(USERFISHINGROD.DURABILITY, USERFISHINGROD.DURABILITY.minus(valueToDecrement))
                    .where(USERFISHINGROD.ID.eq(rodId))
                    .returning()
                    .fetchOne()!!

                val fishRarity = when(selectedFish.rarity) {
                    Rarity.LEGENDARY -> "lendário"
                    Rarity.EPIC -> "épico"
                    Rarity.RARE -> "raro"
                    Rarity.UNCOMUM -> "único"
                    Rarity.COMUM -> "comum"
                    else -> "desconhecido"
                }

                if (newFishingRod.durability!! < 1) {
                    tx.deleteFrom(USERFISHINGROD)
                        .where(USERFISHINGROD.ID.eq(rodId))
                        .and(USERFISHINGROD.USERID.eq(userId))
                        .execute()
                    event.rapidContainerReply(Colors.DANGER, "Sua vara de pesca quebrou após pescar o peixe: **${selectedFish.name}** (raridade: ${fishRarity})")
                    return@transaction
                }
                avaibleFishes--
                Cache.set(key, avaibleFishes, 20, TimeUnit.MINUTES)
                if (avaibleFishes < 1) {
                    event.rapidContainerReply(Colors.DANGER, "${Icon.static.get("Eris_cry")} | Você pescou o peixe: **${selectedFish.name}** (raridade: ${fishRarity}) que custa: **${selectedFish.price}** stx. E todos os peixes do lago foram pescados!")
                    return@transaction
                }

                event.hook.editOriginalComponents(fishingMenu(userId, rodId, round + 1)).useComponentsV2().queue()
                event.hook.sendMessageComponents(ComponentBuilder.ContainerBuilder.create()
                    .withColor(Colors.SUCCESS)
                    .addText("${Icon.static.get("Eris_happy")} | Você pescou o peixe: **${selectedFish.name}** (raridade: ${selectedFish.rarity}) que custa: **${selectedFish.price}** stx")
                    .build()
                ).useComponentsV2().setEphemeral(true).queue()
                GlobalScope.launch {
                    setFishTimeout(event, round + 1, Utils.getRandomLong(1000, 10000), userId)
                }
                LogManage.CreateLog.create()
                    .setLevel(when (selectedFish.rarity) {
                        Rarity.LEGENDARY -> 5
                        Rarity.EPIC -> 4
                        Rarity.RARE -> 3
                        Rarity.UNCOMUM -> 2
                        Rarity.COMUM -> 1
                        else -> 1
                    })
                    .setMessage("pescou o peixe: **${selectedFish.name}** (raridade: ${selectedFish.rarity}) que custa: **${selectedFish.price}** stx")
                    .setUserId(userId)
                    .setTags(listOf("fishing", "fish", selectedFish.rarity.toString().lowercase()))
                    .insert(tx)
            }
        }
    }

    private fun getFishRarity(reactionTime: Long): Rarity {
        // ms para segundos
        val seconds = reactionTime / 1000.0

        // quanto mais rápido, maior o boost em raridades altas
        val weights = mutableMapOf(
            Rarity.LEGENDARY to 2,
            Rarity.EPIC to 10,
            Rarity.RARE to 26,
            Rarity.UNCOMUM to 35,
            Rarity.COMUM to 70
        )

        when {
            seconds <= 0.3 -> {
                weights[Rarity.LEGENDARY] = weights[Rarity.LEGENDARY]!! + 4
                weights[Rarity.EPIC] = weights[Rarity.EPIC]!! + 6
                weights[Rarity.COMUM] = weights[Rarity.COMUM]!! - 5
            }
            seconds <= 1.0 -> {
                weights[Rarity.LEGENDARY] = weights[Rarity.LEGENDARY]!! + 2
                weights[Rarity.EPIC] = weights[Rarity.EPIC]!! + 3
                weights[Rarity.COMUM] = weights[Rarity.COMUM]!! - 3
            }
            seconds > 3.0 -> {
                weights[Rarity.LEGENDARY] = weights[Rarity.LEGENDARY]!! - 2
                weights[Rarity.EPIC] = weights[Rarity.EPIC]!! - 2
                weights[Rarity.COMUM] = weights[Rarity.COMUM]!! + 5
            }
        }

        // Garantir que os pesos não fiquem negativos
        weights.forEach { (rarity, weight) ->
            weights[rarity] = max(weight, 0)
        }

        // normaliza pra somar 100
        val total = weights.values.sum().toDouble()
        val rand = Random.nextDouble(total)

        var acc = 0.0
        for ((rarity, weight) in weights) {
            acc += weight
            if (rand <= acc) return rarity
        }

        return Rarity.COMUM // fallback
    }
}