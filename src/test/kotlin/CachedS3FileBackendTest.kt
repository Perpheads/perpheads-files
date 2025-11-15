package com.perpheads.files

import com.perpheads.files.data.FileData
import com.perpheads.files.repository.FileCacheRepository
import com.perpheads.files.services.CachedS3FileBackend
import com.perpheads.files.services.LocalDiskFileBackend
import com.perpheads.files.services.S3ClientService
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.vertx.mutiny.core.buffer.Buffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.random.Random

@Suppress("UnusedFlow")
class CachedS3FileBackendTest : WordSpec({
    val diskBackend: LocalDiskFileBackend = mockk()
    val s3ClientService: S3ClientService = mockk()
    val fileCacheRepository: FileCacheRepository = mockk()

    afterTest {
        clearAllMocks()
    }

    fun createCache(
        countTarget: Int = 10,
        countThreshold: Int = 15,
        sizeTarget: Long = 10_000,
        sizeThreshold: Long = 15_000,
    ): CachedS3FileBackend {
        return CachedS3FileBackend(
            diskBackend = diskBackend,
            s3ClientService = s3ClientService,
            fileCacheRepository = fileCacheRepository,
            fileCountThreshold = countThreshold,
            fileCountTarget = countTarget,
            fileSizeThreshold = sizeThreshold,
            fileSizeTarget = sizeTarget,
        )
    }

    val dummyArrays = (1..10).map {
        ByteArray(128).apply { Random.nextBytes(this) }
    }

    var fileCounter = 1
    fun getFileData(size: Long = 1000): FileData = FileData(
        fileId = fileCounter++,
        link = "sometext$fileCounter",
        filename = "testing",
        mimeType = "image/jpeg",
        userId = 1,
        uploadDate = Instant.now(),
        size = size
    )

    fun downloadFileToCache(file: FileData) {
        coEvery {
            fileCacheRepository.addFileToCache(file.fileId)
        } just Runs

        val flowSlot = slot<Flow<ByteArray>>()
        coEvery {
            diskBackend.storeFromFlow(file, capture(flowSlot))
        } coAnswers {
            every {
                diskBackend.getFileFlow(file)
            } returns flowSlot.captured.map { Buffer.buffer(it) }
        }

        coEvery {
            fileCacheRepository.markFileAsUsed(file.fileId)
        } just runs

        coEvery {
            s3ClientService.getFile(file.link)
        } coAnswers {
            coEvery {
                diskBackend.getFileFlow(file)
            } returns dummyArrays.asFlow().map { Buffer.buffer(it) }
            dummyArrays.asFlow()
        }
    }

    "The download method" should {
        "download a file not in cache correctly" {
            val file = getFileData()
            val cache = createCache()

            downloadFileToCache(file)
            val returnedFlow = cache.getFileFlow(file)

            returnedFlow.map { it.bytes }.toList() shouldBe dummyArrays

            coVerify(exactly = 1) {
                s3ClientService.getFile(file.link)
                diskBackend.storeFromFlow(file, any())
                fileCacheRepository.addFileToCache(file.fileId)
                fileCacheRepository.markFileAsUsed(file.fileId)
                diskBackend.getFileFlow(file)
            }
            confirmVerified()
        }

        "not download a file twice when it is already in cache and return it from disk" {
            val file = getFileData()
            val cache = createCache()

            downloadFileToCache(file)
            cache.getFileFlow(file).toList()
            clearAllMocks(answers = false)

            val returnedFlow = cache.getFileFlow(file)
            returnedFlow.map { it.bytes }.toList() shouldBe dummyArrays
            coVerify(exactly = 1) {
                diskBackend.getFileFlow(file)
                fileCacheRepository.markFileAsUsed(file.fileId)
            }
            confirmVerified()
        }

        "download file correctly after evicting" {
            val file = getFileData()
            val cache = createCache()

            downloadFileToCache(file)
            cache.getFileFlow(file).toList()
            clearAllMocks(answers = false)

            coEvery {
                diskBackend.delete(file)
            } returns true
            coEvery {
                fileCacheRepository.removeFilesFromCache(listOf(file.fileId))
            } just runs

            cache.evictFromCache(file)
            clearAllMocks(answers = false)

            val returnedFlow = cache.getFileFlow(file)
            returnedFlow.map { it.bytes }.toList() shouldBe dummyArrays

            coVerify(exactly = 1) {
                s3ClientService.getFile(file.link)
                diskBackend.storeFromFlow(file, any())
                fileCacheRepository.addFileToCache(file.fileId)
                fileCacheRepository.markFileAsUsed(file.fileId)
                diskBackend.getFileFlow(file)
            }
            confirmVerified()
        }
    }

    "The evictFromCache method" should {
        "do nothing when evicting a file not in cache" {
            val file = getFileData()
            val cache = createCache()
            cache.evictFromCache(file)
            confirmVerified()
        }

        "evict downloaded file" {
            val file = getFileData()
            val cache = createCache()

            downloadFileToCache(file)
            cache.getFileFlow(file).toList()
            clearAllMocks(answers = false)

            coEvery {
                diskBackend.delete(file)
            } returns true
            coEvery {
                fileCacheRepository.removeFilesFromCache(listOf(file.fileId))
            } just runs

            cache.evictFromCache(file)
            coVerify(exactly = 1) {
                fileCacheRepository.removeFilesFromCache(listOf(file.fileId))
                diskBackend.delete(file)
            }
            confirmVerified()
        }

        "evict downloaded file after downloads finished" {
            val file = getFileData()
            val cache = createCache()

            downloadFileToCache(file)
            cache.getFileFlow(file).toList()
            clearAllMocks(answers = false)

            val waitUntilDownload = CompletableDeferred(Unit)
            val job = launch {
                cache.getFileFlow(file).collect {
                    waitUntilDownload.complete(Unit)
                    delay(50)
                }
            }

            coEvery {
                diskBackend.delete(file)
            } returns true
            coEvery {
                fileCacheRepository.removeFilesFromCache(listOf(file.fileId))
            } just runs

            waitUntilDownload.await()
            cache.evictFromCache(file)
            job.join()
            coVerify(exactly = 1) {
                diskBackend.getFileFlow(file)
                fileCacheRepository.markFileAsUsed(file.fileId)
                fileCacheRepository.removeFilesFromCache(listOf(file.fileId))
                diskBackend.delete(file)
            }
            confirmVerified()
        }
    }
})