package com.docuhyphen.app.api.model.document

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path

class DocumentVersionContentDigestTest
{
    @TempDir
    lateinit var directory: Path

    @Test
    fun `the digest of stored content is its SHA-256 value and byte length`()
    {
        val file = directory.resolve("record.pdf").toFile().apply { writeText("abc") }

        val digest = DocumentVersionContentDigests.of(file)

        assertEquals(DocumentVersionContentHashAlgorithm.SHA_256, digest.algorithm)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest.value)
        assertEquals(3L, digest.length)
    }

    @Test
    fun `empty content has the empty-input digest and no bytes`()
    {
        val file = directory.resolve("empty.pdf").toFile().apply { writeBytes(ByteArray(0)) }

        val digest = DocumentVersionContentDigests.of(file)

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", digest.value)
        assertEquals(0L, digest.length)
    }

    @Test
    fun `copying content states the digest of exactly the bytes that were copied`()
    {
        val output = ByteArrayOutputStream()

        val digest = DocumentVersionContentDigests.copy(ByteArrayInputStream("abc".toByteArray()), output)

        assertEquals("abc", output.toString(Charsets.UTF_8))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest.value)
        assertEquals(3L, digest.length)
    }

    @Test
    fun `a digest value must be a lowercase hexadecimal SHA-256 value`()
    {
        val valid = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertThrows(IllegalArgumentException::class.java) { sha256(valid.uppercase(), 3) }
        assertThrows(IllegalArgumentException::class.java) { sha256(valid.drop(1), 3) }
        assertThrows(IllegalArgumentException::class.java) { sha256("g" + valid.drop(1), 3) }
    }

    @Test
    fun `a digest cannot state a negative byte length`()
    {
        assertThrows(IllegalArgumentException::class.java)
        {
            sha256("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", -1)
        }
    }

    @Test
    fun `the digest is stated as base64 of its raw bytes for a provider checksum`()
    {
        val digest = sha256("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", 3)

        assertEquals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=", digest.base64Value())
    }

    @Test
    fun `server-readable content is verified and end-to-end ciphertext is not`()
    {
        assertEquals(
            DocumentVersionContentVerification.VERIFIED,
            DocumentVersionContentVerification.forEncryptionMode(DocumentEncryptionMode.INTERNAL),
        )
        assertEquals(
            DocumentVersionContentVerification.UNVERIFIED,
            DocumentVersionContentVerification.forEncryptionMode(DocumentEncryptionMode.END_TO_END),
        )
    }

    private fun sha256(value: String, length: Long) =
        DocumentVersionContentDigest(DocumentVersionContentHashAlgorithm.SHA_256, value, length)
}
