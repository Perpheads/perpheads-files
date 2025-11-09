package com.perpheads.files.repository

import com.perpheads.files.utils.transactionCoroutineCtx
import jakarta.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// The DSLContext is available
@Suppress("CdiInjectionPointsInspection")
abstract class AbstractRepository {
    companion object {
        private val currentConfigContextKey = object : CoroutineContext.Key<DSLContextHolderElement> {}
    }

    private class DSLContextHolderElement(val currentContext: DSLContext) :
        AbstractCoroutineContextElement(currentConfigContextKey) {

    }

    /**
     * Use this DSL context to explicitly start statement outside the (possibly existing) current transaction
     */
    @Inject
    open lateinit var freshDsl: DSLContext


    /**
     * If inside a transaction, returns the innermost transaction's DSLContext.
     * If there is no transaction, returns a fresh DSLContext outside any transaction.
     */
    suspend fun dsl(): DSLContext {
        return currentCoroutineContext()[currentConfigContextKey]?.currentContext ?: freshDsl
    }


    // Needs to be protected, running in controllers is NOT safe
    protected suspend fun <T> withTransaction(transactional: suspend () -> T): T {
        return dsl().transactionCoroutineCtx { transaction ->
            withContext(DSLContextHolderElement(transaction)) {
                transactional()
            }
        }
    }
}