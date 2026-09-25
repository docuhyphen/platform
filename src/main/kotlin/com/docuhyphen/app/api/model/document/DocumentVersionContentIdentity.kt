package com.docuhyphen.app.api.model.document

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentVersion
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat

enum class DocumentVersionContentHashAlgorithm(val digestName: String)
{
    SHA_256("SHA-256"),
}

enum class DocumentVersionContentVerification
{
    VERIFIED,
    UNVERIFIED,
    ;

    companion object
    {
        fun forEncryptionMode(encryptionMode: DocumentEncryptionMode): DocumentVersionContentVerification =
            when (encryptionMode)
            {
                DocumentEncryptionMode.INTERNAL -> VERIFIED
                DocumentEncryptionMode.END_TO_END -> UNVERIFIED
            }
    }
}

data class DocumentVersionContentDigest(
    val algorithm: DocumentVersionContentHashAlgorithm,
    val value: String,
    val length: Long,
)
{
    init
    {
        require(SHA_256_HEX.matches(value)) { "A content digest is a lowercase hexadecimal SHA-256 value" }
        require(length >= 0) { "A content digest cannot state a negative byte length" }
    }

    fun base64Value(): String = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(value))

    private companion object
    {
        val SHA_256_HEX = Regex("^[0-9a-f]{64}$")
    }
}

data class StoredDocumentVersionContent(
    val locator: ObjectStoreDocumentVersionLocator,
    val digest: DocumentVersionContentDigest,
)

object DocumentVersionContentDigests
{
    fun of(file: File): DocumentVersionContentDigest =
        file.inputStream().use { input -> copy(input, OutputStream.nullOutputStream()) }

    fun copy(input: InputStream, output: OutputStream): DocumentVersionContentDigest
    {
        val algorithm = DocumentVersionContentHashAlgorithm.SHA_256
        val digest = MessageDigest.getInstance(algorithm.digestName)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var length = 0L

        while (true)
        {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
            output.write(buffer, 0, count)
            length += count
        }

        return DocumentVersionContentDigest(algorithm, HexFormat.of().formatHex(digest.digest()), length)
    }
}

object DocumentVersionContentIdentityMapper
{
    fun recordOn(
        version: DocumentVersion,
        stored: StoredDocumentVersionContent,
        verification: DocumentVersionContentVerification,
    )
    {
        version.storageProvider = stored.locator.provider
        version.storageLocatorKind = stored.locator.kind
        version.storageLocator = stored.locator.value
        version.contentLength = stored.digest.length
        version.contentHashAlgorithm = stored.digest.algorithm
        version.contentHash = stored.digest.value
        version.contentVerification = verification
    }

    fun digestOf(version: DocumentVersion): DocumentVersionContentDigest =
        DocumentVersionContentDigest(version.contentHashAlgorithm, version.contentHash, version.contentLength)
}
