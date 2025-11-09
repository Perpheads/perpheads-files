package com.perpheads.files.repository

import com.perpheads.files.data.User
import com.perpheads.files.db.tables.references.USER
import com.perpheads.files.utils.alphaNumeric
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import java.security.SecureRandom

@ApplicationScoped
class UserRepository : AbstractRepository() {
    private val secureRandom = SecureRandom()

    suspend fun getById(userId: Int): User? {
        return dsl().select()
            .from(USER)
            .where(USER.USER_ID.eq(userId))
            .awaitFirstOrNull()
            ?.into(User::class.java)
    }

    suspend fun getByCommunityId(communityId: Long): User? {
        return dsl().select()
            .from(USER)
            .where(USER.COMMUNITY_ID.eq(communityId))
            .awaitFirstOrNull()
            ?.into(User::class.java)
    }

    suspend fun getByApiKey(apiKey: String): User? {
        return dsl().select()
            .from(USER)
            .where(USER.API_KEY.eq(apiKey))
            .awaitFirstOrNull()
            ?.into(User::class.java)
    }

    private fun generateApiKeyStr(): String {
        return secureRandom.alphaNumeric(32)
    }

    suspend fun generateApiKey(userId: Int): String {
        val newApiKey = generateApiKeyStr()
        dsl().update(USER)
            .set(USER.API_KEY, newApiKey)
            .where(USER.USER_ID.eq(userId))
            .awaitSingle()
        return newApiKey
    }

    suspend fun createUser(communityId: Long, name: String) {
        dsl().insertInto(USER)
            .set(USER.COMMUNITY_ID, communityId)
            .set(USER.NAME, name)
            .awaitSingle()
    }
}