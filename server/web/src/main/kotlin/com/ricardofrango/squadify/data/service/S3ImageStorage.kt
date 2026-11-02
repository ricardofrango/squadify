package com.ricardofrango.squadify.data.service

import com.ricardofrango.squadify.domain.service.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

/**
 * A Railway Storage Bucket (any S3-compatible bucket). Private: the server reads images back and serves
 * them itself (`GET /clubs/{clubId}/logo/{imageId}.png`), so only club members see them.
 */
class S3ImageStorage(
    private val bucket: String,
    endpoint: String,
    region: String,
    accessKeyId: String,
    secretAccessKey: String
) : ImageStorage {
    override val isAvailable: Boolean = true

    private val client: S3Client = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
        .httpClient(UrlConnectionHttpClient.create())
        // Railway buckets use virtual-hosted-style URLs (the bucket as the subdomain).
        .forcePathStyle(false)
        .build()

    override suspend fun put(key: String, bytes: ByteArray, contentType: String) {
        withContext(Dispatchers.IO) {
            client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes)
            )
        }
    }

    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray()
        } catch (e: NoSuchKeyException) {
            null
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
        }
    }
}

/** Production without a bucket: nothing is stored, and uploads say so. */
object UnavailableImageStorage : ImageStorage {
    override val isAvailable: Boolean = false
    override suspend fun put(key: String, bytes: ByteArray, contentType: String) = error("Image storage isn't set up")
    override suspend fun get(key: String): ByteArray? = null
    override suspend fun delete(key: String) {}
}
