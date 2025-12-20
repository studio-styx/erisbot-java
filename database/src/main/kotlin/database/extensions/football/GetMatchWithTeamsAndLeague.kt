package database.extensions.football

import database.repository.football.FootballRepository
import org.jooq.DSLContext

val DSLContext.football
    get() = FootballRepository(this)