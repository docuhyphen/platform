package com.docuhyphen.app.api.model.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class InformationRequestEvidenceFileTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `a file name carrying invisible direction or formatting characters is refused`() {
        listOf(
            "report‮fdp.exe",
            "record‎.pdf",
            "record‏.pdf",
            "⁦record.pdf⁩",
            "‪record.pdf",
            "﻿record.pdf",
            "re​cord.pdf",
        ).forEach { name ->
            val refusal = assertThrows<IllegalArgumentException> { file(name) }

            assertEquals(
                "An evidence file name names one file without a path, control characters or invisible formatting characters",
                refusal.message,
            )
        }
    }

    @Test
    fun `a file name in any script is kept as declared`() {
        listOf(
            "collected-record.pdf",
            "process record 2026.pdf",
            "запись.pdf",
            "記録.pdf",
            "سجل.pdf",
        ).forEach { name ->
            assertEquals(name, file(name).declaredFileName)
        }
    }

    private fun file(name: String) = InformationRequestEvidenceFile(
        file = directory.resolve("content.bin").toFile(),
        declaredFileName = name,
        declaredMediaType = null,
        encryptionMode = DocumentEncryptionMode.INTERNAL,
    )
}
