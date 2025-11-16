# Estrutura: `schedule/transactionExpires`

## Objetivo

Gerenciar automaticamente a expiração de transações pendentes.
O módulo faz duas coisas:

1. **Busca periódica** por transações pendentes com `expiresAt` no futuro.
2. **Agenda individualmente** cada transação para expirar exatamente no horário configurado.

Dessa forma, mesmo após reiniciar o bot, todas as transações ainda pendentes serão reprogramadas.

---

## Arquivos

### **`ExpireTransaction.kt`**

Contém a função responsável por finalizar uma transação quando o horário de expiração chega.

Ela:

* valida se a transação ainda está pendente
* obtém guilda, canal e mensagem vinculados
* tenta editar a mensagem original para indicar a expiração
* atualiza o status no banco de dados (`EXPIRED`)
* aplica fallbacks caso qualquer informação esteja indisponível

---

### **`IntervalCheck.kt`**

Define o processo periódico de varredura.

* Executa a cada 5 minutos (ou intervalo configurado)
* Busca no banco todas as transações pendentes com `expiresAt` maior que 20 minutos do horário atual
* Chama `scheduleTransaction` para cada uma delas

Esse mecanismo garante que o bot reorganize todos os timers após reinicializações.

---

### **`ScheduleTransaction.kt`**

Responsável por agendar a expiração real.

* Calcula o atraso (`delay`) entre agora e `expiresAt`
* Se o horário já passou, expira imediatamente
* Caso contrário, agenda a execução de `expireTransaction` usando o executor global

---

### **`TransactionScheduler.kt`**

Contém o executor compartilhado utilizado para:

* rodar o intervalo periódico (`scheduleAtFixedRate`)
* programar timers individuais de expiração (`schedule`)

Garante que todas as tarefas de expiração sejam executadas de forma assíncrona, sem bloquear threads do bot.
