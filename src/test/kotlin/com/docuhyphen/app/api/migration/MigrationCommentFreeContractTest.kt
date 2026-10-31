package com.docuhyphen.app.api.migration

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class MigrationCommentFreeContractTest {
    @Test
    fun `migrations after the grandfathered range carry no SQL comments`() {
        val directory = Paths.get("src", "main", "resources", "db", "migration")
        val commented = Files.list(directory).use { files ->
            files.toList()
                .filter { versionOf(it)?.let { version -> version > LAST_GRANDFATHERED_VERSION } == true }
                .filter { file ->
                    Files.readAllLines(file).any { line -> line.trim().startsWith("--") || line.contains("/*") }
                }
                .map { it.fileName.toString() }
        }

        assertTrue(commented.isEmpty(), "Migrations with SQL comments: $commented")
    }

    private fun versionOf(file: Path): Int? =
        Regex("^V(\\d+)__.+\\.sql$").matchEntire(file.fileName.toString())?.groupValues?.get(1)?.toInt()

    private companion object {
        const val LAST_GRANDFATHERED_VERSION = 127
    }
}
