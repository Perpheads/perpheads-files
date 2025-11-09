package com.perpheads.files.utils

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.reactivestreams.Publisher


data class CountingQueryResult<T>(val count: Int, val data: List<T>)

class CountingQueryStep(
    private val create: DSLContext,
    private val limit: Int?,
    private val offset: Int?,
    private val fields: Array<out Field<*>>?,
    private val queryBuilder: (SelectSelectStep<*>) -> (SelectLimitStep<*>)
) {
    suspend fun <T> fetch(pojoClass: Class<T>): CountingQueryResult<T> {
        return fetch { it.into(pojoClass) }
    }

    suspend fun <T> fetch(mapper: (Record) -> T): CountingQueryResult<T> {
        val count = create.selectCount().from(queryBuilder(create.selectOne())).awaitSingle().getValue(0, Int::class.java)
        val dataQuery = if (fields != null) {
            queryBuilder(create.select(*fields))
        } else {
            queryBuilder(create.select())
        }
        if (limit != null) {
            dataQuery.limit(limit)
        }
        if (offset != null) {
            dataQuery.offset(offset)
        }

        val data = dataQuery.awaitFlow(mapper)
        return CountingQueryResult(count, data)
    }
}

fun DSLContext.countingQuery(
    limit: Int?,
    offset: Int?,
    vararg fields: Field<*>,
    queryBuilder: (SelectSelectStep<*>) -> (SelectLimitStep<*>)
): CountingQueryStep {
    return CountingQueryStep(this, limit, offset, fields.takeIf { it.isNotEmpty() }, queryBuilder)
}

fun Field<*>.wildcardMatch(str: String): Condition {
    return this.likeIgnoreCase("%" + str.replace("%", "!%") + "%", '!')
}

fun Field<*>.beginsWithMatch(str: String): Condition {
    return this.likeIgnoreCase(str.replace("%", "!%") + "%", '!')
}

suspend fun <T> DSLContext.transactionCoroutineCtx(transactional: suspend (DSLContext) -> T): T {
    return transactionCoroutine {
        transactional(DSL.using(it))
    }
}

suspend fun <T: Any, R> Publisher<T>.awaitFlow(mapper: (T) -> R): List<R> {
    return asFlow().toList().map(mapper)
}

suspend fun <T: Any> Publisher<T>.awaitFlow(): List<T> {
    return asFlow().toList()
}