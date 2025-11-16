package schedules.transactionExpires

import java.util.concurrent.Executors

object TransactionScheduler {
    val executor = Executors.newScheduledThreadPool(4) // ou 1 se preferir serializar
}
