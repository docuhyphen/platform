package com.docuhyphen.app.api.service.audit.archive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Phase 4 gate for [MerkleTree]: deterministic, order-sensitive, and sensitive to any leaf
 * change - the property [AuditArchiver]/[AuditArchiveVerifier] rely on to detect a tampered or
 * truncated segment via [com.docuhyphen.app.api.model.entity.AuditArchiveSegment.merkleRoot].
 */
class MerkleTreeTest
{
    @Test
    fun `same leaves always produce the same root`()
    {
        val leaves = listOf("aa11", "bb22", "cc33", "dd44")
        assertEquals(MerkleTree.computeRoot(leaves), MerkleTree.computeRoot(leaves))
    }

    @Test
    fun `changing any single leaf changes the root`()
    {
        val original = listOf(
            MerkleTree.sha256Hex("a".toByteArray()),
            MerkleTree.sha256Hex("b".toByteArray()),
            MerkleTree.sha256Hex("c".toByteArray()),
        )
        val tampered = listOf(
            MerkleTree.sha256Hex("a".toByteArray()),
            MerkleTree.sha256Hex("TAMPERED".toByteArray()),
            MerkleTree.sha256Hex("c".toByteArray()),
        )

        assertNotEquals(MerkleTree.computeRoot(original), MerkleTree.computeRoot(tampered))
    }

    @Test
    fun `reordering leaves changes the root`()
    {
        val leaves = listOf(
            MerkleTree.sha256Hex("a".toByteArray()),
            MerkleTree.sha256Hex("b".toByteArray()),
            MerkleTree.sha256Hex("c".toByteArray()),
        )
        val reordered = listOf(leaves[1], leaves[0], leaves[2])

        assertNotEquals(MerkleTree.computeRoot(leaves), MerkleTree.computeRoot(reordered))
    }

    @Test
    fun `truncating leaves changes the root`()
    {
        val leaves = listOf(
            MerkleTree.sha256Hex("a".toByteArray()),
            MerkleTree.sha256Hex("b".toByteArray()),
            MerkleTree.sha256Hex("c".toByteArray()),
        )
        val truncated = leaves.dropLast(1)

        assertNotEquals(MerkleTree.computeRoot(leaves), MerkleTree.computeRoot(truncated))
    }

    @Test
    fun `empty leaf list produces a well-defined, non-blank root`()
    {
        val root = MerkleTree.computeRoot(emptyList())
        assertEquals(64, root.length)
    }
}
