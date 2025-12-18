package database.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jooq.Configuration
import org.jooq.DSLContext

suspend fun <T> DSLContext.transactionSuspend(
    block: suspend (Configuration) -> T
): T = withContext(Dispatchers.IO) {
    // O jOOQ precisa de uma thread fixa para a transação JDBC
    this@transactionSuspend.transactionResult { config ->
        // runBlocking aqui é seguro porque estamos explicitamente em Dispatchers.IO
        // e queremos que esta thread específica espere a conclusão das suspensões
        runBlocking {
            block(config)
        }
    }
}