package com.docuhyphen.app.api.service.audit.archive

import java.security.MessageDigest

object MerkleTree
{
    fun computeRoot(leafHexHashes: List<String>): String
    {
        if (leafHexHashes.isEmpty())
        {
            return sha256Hex(ByteArray(0))
        }

        var level: List<ByteArray> = leafHexHashes.map { hexToBytes(it) }
        while (level.size > 1)
        {
            val next = mutableListOf<ByteArray>()
            var i = 0
            while (i < level.size)
            {
                val left = level[i]
                val right = if (i + 1 < level.size) level[i + 1] else level[i]
                next.add(sha256(left + right))
                i += 2
            }
            level = next
        }
        return bytesToHex(level.first())
    }

    fun sha256Hex(bytes: ByteArray): String = bytesToHex(sha256(bytes))

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray
    {
        val clean = hex.trim()
        val out = ByteArray(clean.length / 2)
        for (i in out.indices)
        {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }
}
