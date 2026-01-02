package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.qualifier.Aws
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.bouncycastle.jce.provider.BouncyCastleProvider
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.Key
import java.security.Security
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

@ApplicationScoped
@Aws
class AwsS3FileStorageService @Inject constructor() : FileStorageService
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

    override fun uploadDocument(file: File, key: String): String
    {
        val encryptionKey = generateKey()
        encryptFile(file, encryptionKey)
        PutObjectRequest.builder()
            .bucket("your-bucket-name")
            .key(key)
            .build()
        // s3Client.putObject(putObjectRequest, encryptedFile.toPath())
        return Base64.getEncoder().encodeToString(encryptionKey.encoded)
    }

    override fun downloadDocument(key: String): File
    {
        GetObjectRequest.builder()
            .bucket("your-bucket-name")
            .key(key)
            .build()
        val tempFile = File.createTempFile("s3file", null)
        // s3Client.getObject(getObjectRequest, tempFile.toPath())
        val decodedKey = Base64.getDecoder().decode("your-encryption-key")
        val secretKey = SecretKeySpec(decodedKey, 0, decodedKey.size, ALGORITHM)
        return decryptFile(tempFile, secretKey)
    }

    override fun downloadDocumentsAsZip(keys: List<String>): File
    {
        val tempZipFile = File.createTempFile("documents", ".zip")
        val uniqueKeys = mutableSetOf<String>()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZipFile))).use { zipOut ->
            keys.forEach { key ->
                if (uniqueKeys.add(key))
                {
                    val targetFile = File("document-uploads", key)
                    if (!targetFile.exists())
                    {
                        throw IllegalArgumentException("File not found: $key")
                    }
                    zipOut.putNextEntry(ZipEntry(key))
                    Files.copy(targetFile.toPath(), zipOut)
                    zipOut.closeEntry()
                }
                else
                {
                    // Handle duplicate entry, e.g., by renaming or skipping
                    val newKey = generateUniqueKey(key, uniqueKeys)
                    uniqueKeys.add(newKey)
                    val targetFile = File("document-uploads", key)
                    if (!targetFile.exists())
                    {
                        throw IllegalArgumentException("File not found: $key")
                    }
                    zipOut.putNextEntry(ZipEntry(newKey))
                    Files.copy(targetFile.toPath(), zipOut)
                    zipOut.closeEntry()
                }
            }
        }
        return tempZipFile
    }

    private fun generateUniqueKey(key: String, existingKeys: Set<String>): String
    {
        var newKey = key
        var counter = 1
        while (existingKeys.contains(newKey))
        {
            newKey = "${key}_$counter"
            counter++
        }
        return newKey
    }
}