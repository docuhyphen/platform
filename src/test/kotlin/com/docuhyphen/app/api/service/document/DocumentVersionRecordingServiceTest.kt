package com.docuhyphen.app.api.service.document

import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.repository.document.DocumentVersionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class DocumentVersionRecordingServiceTest {
    private val versionRepository = mock<DocumentVersionRepository>()
    private val service = DocumentVersionRecordingService(mock(), versionRepository, mock(), mock())
    private val documentId = UUID.randomUUID()

    @Test
    fun `the versions of a document are read from its version store`() {
        val versions = listOf(DocumentVersion(), DocumentVersion())
        whenever(versionRepository.findByDocumentId(documentId)).thenReturn(versions)

        assertEquals(versions, service.versionsOf(documentId))
    }

    @Test
    fun `the latest version of a document is read from its version store`() {
        val latest = DocumentVersion()
        whenever(versionRepository.findLatestByDocumentId(documentId)).thenReturn(latest)

        assertSame(latest, service.latestVersionOf(documentId))
        assertNull(service.latestVersionOf(UUID.randomUUID()))
    }
}
