package com.perpheads.files

import com.perpheads.files.data.FileData
import com.perpheads.files.repository.FileCacheRepository
import com.perpheads.files.services.CachedS3FileBackend
import com.perpheads.files.services.LocalDiskFileBackend
import com.perpheads.files.services.S3ClientService
import io.kotest.core.spec.style.WordSpec
import io.mockk.*
import io.vertx.core.http.HttpServerResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Instant

@Suppress("UnusedFlow")
class CachedS3FileBackendTest : WordSpec({
    val diskBackend: LocalDiskFileBackend = mockk()
    val s3ClientService: S3ClientService = mockk()
    val fileCacheRepository: FileCacheRepository = mockk()
    val httpResponse: HttpServerResponse = mockk()

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
            coEvery {
                diskBackend.sendFile(file, any(), any(), httpResponse)
            } just runs
        }

        coEvery {
            fileCacheRepository.markFileAsUsed(file.fileId)
        } just runs

        coEvery {
            s3ClientService.getFile(file.link)
        } coAnswers {
            coEvery {
                diskBackend.sendFile(file, any(), any(), httpResponse)
            } just runs
            flow { ByteArray(4096) }
        }
    }

    "The download method" should {
        "download a file not in cache correctly" {
            val file = getFileData()
            val cache = createCache()

            downloadFileToCache(file)
            cache.sendFile(file, 0, 100, httpResponse)

            coVerify(exactly = 1) {
                s3ClientService.getFile(file.link)
                diskBackend.storeFromFlow(file, any())
                fileCacheRepository.addFileToCache(file.fileId)
                fileCacheRepository.markFileAsUsed(file.fileId)
                diskBackend.sendFile(file, 0, 100, httpResponse)
            }
            confirmVerified()
        }

        "forward range request cache correctly" {
            val file = getFileData()
            val cache = createCache()

            downloadFileToCache(file)
            cache.sendFile(file, 500, 1000, httpResponse)

            coVerify(exactly = 1) {
                s3ClientService.getFile(file.link)
                diskBackend.storeFromFlow(file, any())
                fileCacheRepository.addFileToCache(file.fileId)
                fileCacheRepository.markFileAsUsed(file.fileId)
                diskBackend.sendFile(file, 500, 1000, httpResponse)
            }
            confirmVerified()
        }

        "not download a file twice when it is already in cache and return it from disk" {
            val file = getFileData()
            val cache = createCache()

            downloadFileToCache(file)
            cache.sendFile(file, 0, 1000, httpResponse)
            clearAllMocks(answers = false)

            cache.sendFile(file, 0, 1000, httpResponse)
            coVerify(exactly = 1) {
                diskBackend.sendFile(file, 0, 1000, httpResponse)
                fileCacheRepository.markFileAsUsed(file.fileId)
            }
            confirmVerified()
        }

        "download file correctly after evicting" {
            val file = getFileData()
            val cache = createCache()

            downloadFileToCache(file)
            cache.sendFile(file, 0, 1000, httpResponse)
            clearAllMocks(answers = false)

            coEvery {
                diskBackend.delete(file)
            } returns true
            coEvery {
                fileCacheRepository.removeFilesFromCache(listOf(file.fileId))
            } just runs

            cache.evictFromCache(file)
            clearAllMocks(answers = false)

            cache.sendFile(file, 0, 1000, httpResponse)

            coVerify(exactly = 1) {
                s3ClientService.getFile(file.link)
                diskBackend.storeFromFlow(file, any())
                fileCacheRepository.addFileToCache(file.fileId)
                fileCacheRepository.markFileAsUsed(file.fileId)
                cache.sendFile(file, 0, 1000, httpResponse)
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
            cache.sendFile(file, 0, 1000, httpResponse)
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
            cache.sendFile(file, 0, 1000, httpResponse)
            clearAllMocks(answers = false)

            val waitUntilDownload = CompletableDeferred(Unit)

            coEvery {
                diskBackend.sendFile(file, any(), any(), httpResponse)
            } coAnswers {
                delay(500)
            }

            val job = launch {
                cache.sendFile(file, 0, 1000, httpResponse)
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
                diskBackend.sendFile(file, 0, 1000, httpResponse)
                fileCacheRepository.markFileAsUsed(file.fileId)
                fileCacheRepository.removeFilesFromCache(listOf(file.fileId))
                diskBackend.delete(file)
            }
            confirmVerified()
        }
    }
})