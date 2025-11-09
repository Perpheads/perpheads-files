package com.perpheads.files.repository

import com.perpheads.files.data.User
import com.perpheads.files.db.tables.Cookie.Companion.COOKIE
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.impl.DSL
import java.time.Instant

@ApplicationScoped
class CookieRepository(
    private val userRepository: UserRepository,
) : AbstractRepository() {
    private val cookieValidCondition = COOKIE.EXPIRY_DATE.gt(DSL.currentInstant())

    suspend fun create(token: String, userId: Int, expiryDate: Instant) {
        dsl().insertInto(COOKIE)
            .set(COOKIE.TOKEN, token)
            .set(COOKIE.USER_ID, userId)
            .set(COOKIE.EXPIRY_DATE, expiryDate)
            .awaitSingle()
    }

    suspend fun getUserForCookie(id: String): User? = withTransaction {
        dsl().select(COOKIE.USER_ID)
            .from(COOKIE)
            .where(COOKIE.TOKEN.eq(id))
            .and(cookieValidCondition)
            .awaitFirstOrNull()
            ?.value1()?.let {
                userRepository.getById(it)
            }

    }

    suspend fun delete(id: String): Boolean {
        return dsl().deleteFrom(COOKIE)
            .where(COOKIE.TOKEN.eq(id))
            .awaitSingle() == 1
    }

    suspend fun deleteAllByUser(userId: Int) {
        dsl().deleteFrom(COOKIE)
            .where(COOKIE.USER_ID.eq(userId))
            .awaitSingle()
    }

    suspend fun deleteExpired(): Int {
        return dsl().deleteFrom(COOKIE)
            .where(!cookieValidCondition)
            .awaitSingle()
    }
}