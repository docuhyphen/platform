package com.docuhyphen.app.api.service.exchange

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DocumentContentHashServiceTest
{
    @Test
    fun `sha256 returns the content digest`()
    {
        val file = Files.createTempFile("document-hash-", ".txt").toFile()
        file.writeText("DocuHyphen")
        try
        {
            assertEquals(
                "11563ded9555ab76efa0d493b66cce48c43e43017bd1c6ae8816ba417dbb0780",
                DocumentContentHashService().sha256(file),
            )
        }
        finally
        {
            file.delete()
        }
    }
}
