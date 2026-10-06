package com.perpheads.files.repository

import com.perpheads.files.data.FileData
import com.perpheads.files.data.FileResponse
import com.perpheads.files.data.FileOverallStatistics
import com.perpheads.files.data.FileUserStatistics
import com.perpheads.files.db.tables.references.FILE
import com.perpheads.files.db.tables.references.FILE_THUMBNAIL
import com.perpheads.files.db.tables.references.USER
import com.perpheads.files.utils.alphaNumeric
import com.perpheads.files.utils.awaitFlow
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.impl.DSL
import java.security.SecureRandom


@ApplicationScoped
class FileRepository : AbstractRepository() {
    private val secureRandom = SecureRandom()

    suspend fun createThumbnail(fileId: Int, thumbnail: ByteArray) {
        dsl().insertInto(FILE_THUMBNAIL)
            .set(FILE_THUMBNAIL.THUMBNAIL, thumbnail)
            .set(FILE_THUMBNAIL.FILE_ID, fileId)
            .awaitSingle()
    }

    suspend fun rename(fileId: Int, name: String) {
        dsl().update(FILE)
            .set(FILE.FILENAME, name)
            .where(FILE.FILE_ID.eq(fileId))
            .awaitSingle()
    }

    private fun newLink(currentLink: String): String = secureRandom.alphaNumeric(16) + currentLink.drop(16)

    suspend fun regenerateLink(file: FileData): String {
        val link = newLink(file.link)
        dsl().update(FILE)
            .set(FILE.LINK, link)
            .where(FILE.FILE_ID.eq(file.fileId))
            .awaitSingle()
        return link
    }

    suspend fun regenerateAllLinks(): Int = withTransaction {
        val files = dsl().select(FILE.FILE_ID, FILE.LINK)
            .from(FILE)
            .awaitFlow { it[FILE.FILE_ID]!! to it[FILE.LINK]!! }
        for (chunk in files.chunked(1000)) {
            val newLinks = DSL.values(*chunk.map { (fileId, link) -> DSL.row(fileId, newLink(link)) }.toTypedArray())
                .`as`("new_links", "file_id", "link")
            dsl().update(FILE)
                .set(FILE.LINK, newLinks.field("link", FILE.LINK.dataType)!!)
                .from(newLinks)
                .where(FILE.FILE_ID.eq(newLinks.field("file_id", FILE.FILE_ID.dataType)!!))
                .awaitSingle()
        }
        files.size
    }

    suspend fun getThumbnail(
        link: String,
    ): ByteArray? {
        return dsl().select(FILE_THUMBNAIL.THUMBNAIL)
            .from(FILE_THUMBNAIL)
            .join(FILE).on(FILE.FILE_ID.eq(FILE_THUMBNAIL.FILE_ID))
            .where(FILE.LINK.eq(link))
            .and(FILE.UPLOADED.isTrue)
            .awaitFirstOrNull()
            ?.value1()
    }

    suspend fun findFileIds(): List<Int> {
        return dsl().select(FILE.FILE_ID)
            .from(FILE)
            .where(FILE.UPLOADED.isTrue)
            .orderBy(FILE.FILE_ID.desc())
            .awaitFlow { it[FILE.FILE_ID]!! }
    }

    suspend fun findFiles(
        userId: Int,
        beforeId: Int?,
        offset: Int,
        limit: Int,
        searchStr: String?
    ): Pair<Int, List<FileResponse>> {
        var condition = FILE.USER_ID.eq(userId)

        if (beforeId != null) {
            condition = condition.and(FILE.FILE_ID.lt(beforeId))
        }
        if (searchStr != null) {
            condition = condition.and(FILE.FILENAME.likeIgnoreCase("%" + searchStr.replace("%", "!%") + "%", '!'))
        }

        val fields = FILE.fields() + FILE_THUMBNAIL.THUMBNAIL.isNotNull.`as`("has_thumbnail")
        val query = dsl().select(*fields)
            .from(FILE)
            .leftJoin(FILE_THUMBNAIL).on(FILE.FILE_ID.eq(FILE_THUMBNAIL.FILE_ID))
            .where(condition)
            .and(FILE.UPLOADED.isTrue)
            .orderBy(FILE.FILE_ID.desc())
        val fileCount = dsl().selectCount().from(FILE)
            .where(condition)
            .and(FILE.UPLOADED.isTrue)
            .awaitSingle().value1()
        val files = query.offset(offset).limit(limit).awaitFlow { it.into(FileResponse::class.java) }
        return fileCount to files
    }

    suspend fun create(file: FileData): FileData {
        val record = dsl().newRecord(FILE)
        record.from(file)
        record.fileId = null
        record.touched(FILE.FILE_ID, false)

        val insertedFile = dsl().insertInto(FILE)
            .set(record)
            .returning(FILE.FILE_ID)
            .awaitSingle()
        return file.copy(fileId = insertedFile.fileId!!)
    }

    suspend fun markFileAsUploaded(fileId: Int) {
        dsl().update(FILE)
            .set(FILE.UPLOADED, true)
            .where(FILE.FILE_ID.eq(fileId))
            .awaitFirst()
    }

    suspend fun findByLink(link: String): FileData? {
        return dsl().select()
            .from(FILE)
            .where(FILE.LINK.eq(link))
            .and(FILE.UPLOADED.isTrue)
            .awaitFirstOrNull()
            ?.into(FileData::class.java)
    }

    suspend fun findById(fileId: Int): FileData? {
        return dsl().select()
            .from(FILE)
            .where(FILE.FILE_ID.eq(fileId))
            .and(FILE.UPLOADED.isTrue)
            .awaitFirstOrNull()
            ?.into(FileData::class.java)
    }

    suspend fun delete(fileId: Int) {
        dsl().deleteFrom(FILE)
            .where(FILE.FILE_ID.eq(fileId))
            .awaitSingle()
    }

    suspend fun getOverallStatistics(): FileOverallStatistics {
        return dsl().select(DSL.count(FILE.FILE_ID), DSL.sum(FILE.SIZE))
            .from(FILE)
            .where(FILE.UPLOADED.isTrue)
            .awaitSingle().let {
                FileOverallStatistics(
                    fileCount = it.value1(),
                    storageUsed = it.value2().toLong()
                )
            }
    }

    suspend fun getUserStatistics(): List<FileUserStatistics> {
        val sumField = DSL.sum(FILE.SIZE).`as`("sum")
        return dsl().select(USER.NAME, USER.COMMUNITY_ID, DSL.count(FILE.FILE_ID), sumField)
            .from(USER)
            .join(FILE).on(FILE.USER_ID.eq(USER.USER_ID))
            .groupBy(USER.NAME, USER.COMMUNITY_ID)
            .orderBy(sumField.desc())
            .limit(100)
            .awaitFlow {
                FileUserStatistics(
                    name = it.value1()!!,
                    communityId = it.value2()!!,
                    fileCount = it.value3(),
                    storageUsed = it.value4().toLong()
                )
            }
    }
}