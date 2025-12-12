package studio.styx.erisbot.discord.features.listeners

import net.dv8tion.jda.api.JDA
import org.jooq.DSLContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import schedules.GiveawayExpires
import schedules.transactionExpires.startIntervalCheck

class OnReady(private val jda: JDA, private val dsl: DSLContext) {

    fun startSchedules() {
        // 1. Inicia a função suspensa em uma nova Thread (IO ou Default) sem travar o Java
        CoroutineScope(Dispatchers.IO).launch {
            initGiveaway()
        }

        // 2. Continua a execução normal das outras funções
        initTransactionExpires()
    }

    private suspend fun initGiveaway() {
        GiveawayExpires(jda, dsl).infinitelyScheduleGiveaway()
    }

    fun initTransactionExpires() {
        startIntervalCheck(dsl, jda)
    }
}