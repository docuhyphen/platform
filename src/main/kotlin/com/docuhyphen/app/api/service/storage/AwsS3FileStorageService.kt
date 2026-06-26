package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.qualifier.Aws
import jakarta.enterprise.context.ApplicationScoped
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.nio.file.Files
import java.security.Key
import java.security.Security
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import software.amazon.awssdk.core.sync.ResponseTransformer

@ApplicationScoped
@Aws
class AwsS3FileStorageService : FileStorageService
{

    companion object
    {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES"
        private const val ENABLE_ENCRYPTION = false

        private const val BUCKET_NAME = "docuhyphen-demo-documents"

        // Hardcoded for now
        private const val AWS_ACCESS_KEY = \"REDACTED_AWS_ACCESS_KEY\"
        private const val AWS_SECRET_KEY = \"REDACTED_AWS_SECRET_KEY\"
        private const val AWS_REGION = "us-east-1"

        init
        {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val s3Client: S3Client = createS3Client()

    /* ============================
       S3 Client Initialization
       ============================ */

    private fun createS3Client(): S3Client
    {
        val credentials = AwsBasicCredentials.create(
            AWS_ACCESS_KEY,
            AWS_SECRET_KEY
        )

        return S3Client.builder()
            .region(Region.of(AWS_REGION))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build()
    }

    /* ============================
       Encryption helpers
       ============================ */

    private fun generateKey(): Key
    {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(256)
        return keyGen.generateKey()
    }

    private fun encryptFile(file: File, key: Key): File
    {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encryptedBytes = cipher.doFinal(Files.readAllBytes(file.toPath()))
        val encryptedFile = File(file.parent, "encrypted_${file.name}")
        Files.write(encryptedFile.toPath(), encryptedBytes)
        return encryptedFile
    }

    private fun decryptFile(file: File, key: Key): File
    {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decryptedBytes = cipher.doFinal(Files.readAllBytes(file.toPath()))
        val decryptedFile = File(file.parent, "decrypted_${file.name}")
        Files.write(decryptedFile.toPath(), decryptedBytes)
        return decryptedFile
    }

    /* ============================
       Upload
       ============================ */

    override fun uploadDocument(file: File, key: String): String
    {
        val encryptionKey = if (ENABLE_ENCRYPTION) generateKey() else null

        val fileToUpload =
            if (ENABLE_ENCRYPTION && encryptionKey != null)
            {
                encryptFile(file, encryptionKey)
            }
            else
            {
                file
            }

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(BUCKET_NAME)
            .key(key)
            .contentType(Files.probeContentType(fileToUpload.toPath()))
            .build()

        s3Client.putObject(putObjectRequest, fileToUpload.toPath())

        return encryptionKey?.let {
            Base64.getEncoder().encodeToString(it.encoded)
        } ?: ""
    }

    /* ============================
       Download
       ============================ */

    override fun downloadDocument(key: String): File {
        // Preserve the real file extension so callers can use file.name reliably
        val extension = "." + key.substringAfterLast('.', "tmp")
        val tempFile = Files.createTempFile("docuhyphen-${UUID.randomUUID()}", extension).toFile()

        val getObjectRequest = GetObjectRequest.builder()
            .bucket(BUCKET_NAME)
            .key(key)
            .build()

        // Use OutputStream transformer
        tempFile.outputStream().use { outputStream ->
            s3Client.getObject(getObjectRequest, ResponseTransformer.toOutputStream(outputStream))
        }

        if (!ENABLE_ENCRYPTION) {
            return tempFile
        }

        // Future decryption path
        val decodedKey = Base64.getDecoder().decode("REPLACE_WITH_REAL_KEY")
        val secretKey = SecretKeySpec(decodedKey, ALGORITHM)
        return decryptFile(tempFile, secretKey)
    }

    /* ============================
       Download ZIP
       ============================ */

    override fun downloadDocumentsAsZip(keys: List<String>): File
    {
        val zipFile = File.createTempFile("documents-", ".zip")

        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zipOut ->
            keys.distinct().forEach { key ->
                val tempFile = downloadDocument(key)
                zipOut.putNextEntry(java.util.zip.ZipEntry(key))
                Files.copy(tempFile.toPath(), zipOut)
                zipOut.closeEntry()
                tempFile.delete()
            }
        }

        return zipFile
    }

    override fun getDocumentSizeBytes(key: String): Long
    {
        val headObjectRequest = HeadObjectRequest.builder()
            .bucket(BUCKET_NAME)
            .key(key)
            .build()

        return s3Client.headObject(headObjectRequest).contentLength()
    }
}
