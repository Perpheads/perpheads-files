package com.perpheads.files.services

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.NotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import org.eclipse.microprofile.config.inject.ConfigProperty
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.core.async.ResponsePublisher
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.net.URI

@ApplicationScoped
class S3ClientService(
    @ConfigProperty(name = "s3.bucket.endpoint")
    endpoint: String,
    @ConfigProperty(name = "s3.bucket.access_key")
    accessKey: String,
    @ConfigProperty(name = "s3.bucket.secret_key")
    secretKey: String,
    @param:ConfigProperty(name = "s3.bucket.name")
    private val bucketName: String,
) {
    private val bucketUrl = URI.create("https://$endpoint")

    private val s3Client = S3AsyncClient.builder()
        .endpointOverride(bucketUrl)
        .region(Region.of(bucketUrl.host.substringBefore('.')))
        .credentialsProvider {
            AwsBasicCredentials.create(accessKey, secretKey)
        }
        .build()

    suspend fun getFile(filename: String): Flow<ByteArray> = flow {
        val objectResponse = runCatching {
            s3Client.getObject(
                GetObjectRequest.builder()
                    .key(filename)
                    .bucket(bucketName)
                    .build(),
                AsyncResponseTransformer.toPublisher()
            ).await()
        }.getOrNull() ?: throw NotFoundException()

        emitAll(objectResponse.asFlow().map { it.array() })
    }

    suspend fun uploadFile(
        file: File,
        filename: String,
        contentType: String,
    ) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .key(filename)
                .bucket(bucketName)
                .contentType(contentType)
                .contentLength(file.length())
                .build(),
            AsyncRequestBody.fromFile(file)
        ).await()
    }

    suspend fun deleteFile(filename: String): Boolean {
        return runCatching {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .key(filename)
                    .bucket(bucketName)
                    .build()
            ).await()
        }.isSuccess
    }
}