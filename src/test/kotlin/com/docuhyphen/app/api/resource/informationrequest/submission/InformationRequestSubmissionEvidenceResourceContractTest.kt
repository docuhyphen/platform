package com.docuhyphen.app.api.resource.informationrequest.submission

import com.docuhyphen.app.api.model.document.DocumentVersionContent
import com.docuhyphen.app.api.model.document.DocumentVersionReadHandle
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContent
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.resource.informationrequest.submission.operations.InformationRequestSubmissionEvidenceResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionEvidenceQueryService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.StreamingOutput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class InformationRequestSubmissionEvidenceResourceContractTest {
    private val requestId = UUID.randomUUID()
    private val packageId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "user-session"))

    private val evidenceQueries: InformationRequestSubmissionEvidenceQueryService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val resource = InformationRequestSubmissionEvidenceResource(evidenceQueries, accessContextFactory)

    init {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `a submitted file is read through its own package of the request`() {
        assertEquals(
            "/information-requests/{id}/submissions/{packageId}/evidence",
            InformationRequestSubmissionEvidenceResourceOperations::class.java.getAnnotation(Path::class.java).value,
        )
        mapOf("download" to "/{versionId}/content", "preview" to "/{versionId}/preview").forEach { (name, path) ->
            val method =
                InformationRequestSubmissionEvidenceResourceOperations::class.java.methods.single { it.name == name }
            assertEquals(path, method.getAnnotation(Path::class.java).value)
            assertEquals(listOf(MediaType.WILDCARD), method.getAnnotation(Produces::class.java).value.toList())
        }
    }

    @Test
    fun `a download delegates the exact package and version and answers an attachment a browser will not run`() {
        whenever(
            evidenceQueries.openContent(
                requestId,
                packageId,
                versionId,
                access,
                InformationRequestEvidenceContentUse.DOWNLOAD
            )
        )
            .thenReturn(content("submitted \"record\".pdf", inline = false))

        val response = resource.download(requestId.toString(), packageId.toString(), versionId.toString())

        assertEquals(200, response.status)
        assertEquals("application/pdf", response.mediaType.toString())
        assertEquals(
            "attachment; filename=\"submitted _record_.pdf\"; filename*=UTF-8''submitted%20%22record%22.pdf",
            response.getHeaderString("Content-Disposition"),
        )
        assertEquals("nosniff", response.getHeaderString("X-Content-Type-Options"))
        assertEquals("sandbox; default-src 'none'", response.getHeaderString("Content-Security-Policy"))
        assertEquals("no-store", response.getHeaderString("Cache-Control"))
        assertArrayEquals(
            PDF.toByteArray(),
            ByteArrayOutputStream().also { (response.entity as StreamingOutput).write(it) }.toByteArray()
        )
    }

    @Test
    fun `a preview delegates the exact package and version and answers the content inline`() {
        whenever(
            evidenceQueries.openContent(
                requestId,
                packageId,
                versionId,
                access,
                InformationRequestEvidenceContentUse.PREVIEW
            )
        )
            .thenReturn(content("submitted-record.pdf", inline = true))

        val response = resource.preview(requestId.toString(), packageId.toString(), versionId.toString())

        assertEquals(200, response.status)
        assertTrue(response.getHeaderString("Content-Disposition").startsWith("inline; "))
    }

    @Test
    fun `refusals keep their meaning and malformed identifiers never reach the service`() {
        whenever(evidenceQueries.openContent(any(), any(), any(), any(), any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Submission Package evidence not found"
                )
            )
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.EVIDENCE_CONTENT_QUARANTINED,
                    "Quarantined"
                )
            )
            .thenThrow(ForbiddenException("Denied"))
            .thenThrow(IllegalArgumentException("Information Request not found"))

        val missing = resource.download(requestId.toString(), packageId.toString(), versionId.toString())
        assertEquals(404, missing.status)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, (missing.entity as ResponseError).reasonCode)
        val quarantined = resource.preview(requestId.toString(), packageId.toString(), versionId.toString())
        assertEquals(409, quarantined.status)
        assertEquals(
            InformationRequestErrorCatalog.EVIDENCE_CONTENT_QUARANTINED,
            (quarantined.entity as ResponseError).reasonCode
        )
        assertEquals(403, resource.download(requestId.toString(), packageId.toString(), versionId.toString()).status)
        assertEquals(404, resource.download(requestId.toString(), packageId.toString(), versionId.toString()).status)

        assertEquals(400, resource.download("not-a-request", packageId.toString(), versionId.toString()).status)
        assertEquals(400, resource.download(requestId.toString(), "not-a-package", versionId.toString()).status)
        assertEquals(400, resource.preview(requestId.toString(), packageId.toString(), "not-a-version").status)
        verify(evidenceQueries, times(4)).openContent(any(), any(), any(), any(), any())
    }

    @Test
    fun `an unauthenticated caller is refused before the service runs`() {
        whenever(accessContextFactory.currentAuthenticated()).thenThrow(io.quarkus.security.UnauthorizedException("Sign in"))

        assertEquals(401, resource.download(requestId.toString(), packageId.toString(), versionId.toString()).status)
        verify(evidenceQueries, never()).openContent(any(), any(), any(), any(), any())
    }

    private fun content(fileName: String, inline: Boolean): InformationRequestEvidenceContent {
        val file = File.createTempFile("submitted-evidence", ".pdf").apply {
            deleteOnExit()
            writeText(PDF)
        }
        return InformationRequestEvidenceContent(
            DocumentVersionContent(DocumentVersionReadHandle.storedFile(file), fileName),
            fileName,
            "application/pdf",
            inline,
        )
    }

    private companion object {
        const val PDF = "%PDF-1.4\n%%EOF\n"
    }
}
