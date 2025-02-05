// AwsS3Service.kt
package com.dochyphen.app.api.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.bouncycastle.jce.provider.BouncyCastleProvider
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.nio.file.Files
import java.security.Key
import java.security.Security
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

@ApplicationScoped
class AwsS3Service @Inject constructor(
//    private val s3Client: S3Client
)
{
    companion object
    {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES"

        init
        {
            Security.addProvider(BouncyCastleProvider())
        }
    }

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
        val inputBytes = Files.readAllBytes(file.toPath())
        val outputBytes = cipher.doFinal(inputBytes)
        val encryptedFile = File(file.parent, "encrypted_${file.name}")
        Files.write(encryptedFile.toPath(), outputBytes)
        return encryptedFile
    }

    private fun decryptFile(file: File, key: Key): File
    {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key)
        val inputBytes = Files.readAllBytes(file.toPath())
        val outputBytes = cipher.doFinal(inputBytes)
        val decryptedFile = File(file.parent, "decrypted_${file.name}")
        Files.write(decryptedFile.toPath(), outputBytes)
        return decryptedFile
    }

    fun uploadDocument(file: File, bucketName: String, key: String): String
    {
        val encryptionKey = generateKey()
        val encryptedFile = encryptFile(file, encryptionKey)
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
//        s3Client.putObject(putObjectRequest, encryptedFile.toPath())
        return Base64.getEncoder().encodeToString(encryptionKey.encoded)
    }

    fun downloadDocument(bucketName: String, key: String, encryptionKey: String): File
    {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        val tempFile = File.createTempFile("s3file", null)
//        s3Client.getObject(getObjectRequest, tempFile.toPath())
        val decodedKey = Base64.getDecoder().decode(encryptionKey)
        val secretKey = SecretKeySpec(decodedKey, 0, decodedKey.size, ALGORITHM)
        return decryptFile(tempFile, secretKey)
    }
}