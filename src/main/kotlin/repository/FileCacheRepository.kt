package com.perpheads.files.repository

import com.perpheads.files.data.FileData
import com.perpheads.files.db.tables.references.FILE
import com.perpheads.files.db.tables.references.FILE_CACHE
import com.perpheads.files.utils.awaitFlow
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.impl.DSL
import java.time.Instant

@ApplicationScoped
class FileCacheRepository : AbstractRepository() {
    class FileCacheEntry(val file: FileData, val lastUsed: Instant)

    suspend fun addFileToCache(fileId: Int) {
        dsl().insertInto(FILE_CACHE)
            .set(FILE_CACHE.FILE_ID, fileId)
            .set(FILE_CACHE.LAST_USED, DSL.currentInstant())
            .onDuplicateKeyUpdate()
            .set(FILE_CACHE.LAST_USED, DSL.currentInstant())
            .awaitSingle()
    }

    suspend fun markFileAsUsed(fileId: Int) {
        dsl().update(FILE_CACHE)
            .set(FILE_CACHE.LAST_USED, DSL.currentInstant())
            .where(FILE_CACHE.FILE_ID.eq(fileId))
            .awaitSingle()
    }

    suspend fun removeFilesFromCache(fileIds: List<Int>) {
        if (fileIds.isEmpty()) return
        dsl().deleteFrom(FILE_CACHE)
            .where(FILE_CACHE.FILE_ID.`in`(fileIds))
            .awaitSingle()
    }

    suspend fun getFileCache(): List<FileCacheEntry> {
        return dsl().select()
            .from(FILE_CACHE)
            .join(FILE).on(FILE_CACHE.FILE_ID.eq(FILE.FILE_ID))
            .awaitFlow {
                FileCacheEntry(
                    file = it.into(FileData::class.java),
                    lastUsed = it[FILE_CACHE.LAST_USED]!!
                )
            }
    }
}