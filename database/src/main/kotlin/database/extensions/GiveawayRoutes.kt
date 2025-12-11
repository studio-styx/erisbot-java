package database.extensions

import database.repository.giveaway.GiveawayQueryBuilder
import database.repository.giveaway.GiveawayRepository
import org.jooq.DSLContext

val DSLContext.giveaways
    get() = GiveawayRepository(this)

fun DSLContext.giveaway(id: Int) = GiveawayQueryBuilder(this, id)