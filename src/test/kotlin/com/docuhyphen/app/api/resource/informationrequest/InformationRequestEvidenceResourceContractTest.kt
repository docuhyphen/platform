package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestEvidenceCommandResultDto
import com.docuhyphen.app.api.model.dto.InformationRequestEvidenceListDto
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceArtifactView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceAttributes
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCommandResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceContent
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceContentUse
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceCoverage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceList
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoAuthAccess
import com.docuhyphen.app.api.model.informationrequest.ReplaceInformationRequestEvidenceCommand
import com.docuhyphen.app.api.model.informationrequest.UploadInformationRequestEvidenceCommand
import com.docuhyphen.app.api.resource.model.InformationRequestEvidenceStateChangeRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestEvidenceUploadService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.Path
import org.jboss.resteasy.reactive.multipart.FileUpload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InformationRequestEvidenceResourceContractTest
{
    private val requestId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val access = RequestAccessContext(principal, AuthorizationContext(sessionRef = "user-session"))

    private val uploadService: InformationRequestEvidenceUploadService = mock()
    private val collectionService: InformationRequestEvidenceCollectionService = mock()
    private val queryService: InformationRequestEvidenceQueryService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val readAccessService: InformationRequestNoAuthReadAccessService = mock()

    private val artifact = InformationRequestEvidenceArtifact().apply {
        informationRequestId = requestId
        informationRequestRequirementId = requirementId
        artifactKey = "evidence-1"
        createdByPrincipalKind = principal.kind
        createdByPrincipalId = principal.id
        createdAt = Timestamp.from(Instant.now())
        updatedAt = createdAt
    }
    private val result = InformationRequestEvidenceCommandResult(
        artifact = InformationRequestEvidenceArtifactView(artifact, emptyList()),
        evidenceETag = "\"$requirementId:1\"",
        artifactETag = "\"${artifact.id}:1\"",
    )

    private val resource = InformationRequestEvidenceResource(uploadService, collectionService, queryService, accessContextFactory)
    private val noAuthResource = InformationRequestNoAuthEvidenceResource(uploadService, collectionService, queryService, readAccessService)

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `evidence is routed under the exact Requirement occurrence on both surfaces`()
    {
        assertEquals(
            "/information-requests/{id}/requirements/{requirementId}/evidence-artifacts",
            InformationRequestEvidenceResource::class.java.getAnnotation(Path::class.java).value,
        )
        val noAuthPath = InformationRequestNoAuthEvidenceResource::class.java.getAnnotation(Path::class.java).value
        assertEquals("no-auth/information-requests/{id}/requirements/{requirementId}/evidence-artifacts", noAuthPath)
        assertTrue("/$noAuthPath".startsWith("/no-auth/information-requests/"))
    }

    @Test
    fun `an upload delegates the parsed file, attributes, precondition, and key and answers the created artifact`()
    {
        whenever(uploadService.upload(any())).thenReturn(result)

        val response = upload(
            resource,
            issuedOn = "2026-01-10",
            coverageStartsOn = "2026-01-01",
            coverageEndsOn = "2026-06-30",
            issuer = " Process Registry ",
        )

        assertEquals(201, response.status)
        assertEquals(result.artifactETag, response.getHeaderString("ETag"))
        assertEquals(result.evidenceETag, response.getHeaderString(InformationRequestEvidenceHttp.EVIDENCE_ETAG_HEADER))
        val body = response.entity as InformationRequestEvidenceCommandResultDto
        assertEquals(artifact.id, body.artifact.id)
        assertEquals(true, body.artifact.createdByCaller)

        val command = argumentCaptor<UploadInformationRequestEvidenceCommand>().also { verify(uploadService).upload(it.capture()) }.firstValue
        assertEquals(requestId, command.requestId)
        assertEquals(requirementId, command.requirementId)
        assertEquals(access, command.access)
        assertEquals(CommandPrecondition.ExpectedRevision("\"$requirementId:0\""), command.precondition)
        assertEquals("upload-key", command.idempotencyKey)
        assertEquals("collected-record.pdf", command.file.declaredFileName)
        assertEquals("application/pdf", command.file.declaredMediaType)
        assertEquals(DocumentEncryptionMode.INTERNAL, command.file.encryptionMode)
        assertEquals(
            InformationRequestEvidenceAttributes(
                issuer = "Process Registry",
                issuedOn = LocalDate.of(2026, 1, 10),
                coverage = InformationRequestEvidenceCoverage(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
            ),
            command.attributes,
        )
    }

    @Test
    fun `an upload without its key, file, or valid attributes is refused before the service runs`()
    {
        assertEquals(400, upload(resource, idempotencyKey = null).status)
        assertEquals(400, upload(resource, file = null).status)
        assertEquals(400, upload(resource, issuedOn = "10/01/2026").status)
        assertEquals(400, upload(resource, coverageStartsOn = "2026-01-01").status)
        assertEquals(400, upload(resource, encryptionMode = "SEALED").status)
        assertEquals(400, upload(resource, issuer = "   ", issuedOn = "2026-02-01", expiresOn = "2026-01-01").status)
        verify(uploadService, never()).upload(any())
    }

    @Test
    fun `a missing precondition is answered 428 with the current evidence ETag`()
    {
        whenever(uploadService.upload(any())).thenThrow(CommandPreconditionException.required("\"$requirementId:3\""))

        val response = upload(resource, ifMatch = null)

        assertEquals(428, response.status)
        assertEquals("\"$requirementId:3\"", response.getHeaderString("ETag"))
    }

    @Test
    fun `a replacement names the artifact it replaces`()
    {
        whenever(uploadService.replace(any())).thenReturn(result)

        val response = resource.replace(
            requestId.toString(), requirementId.toString(), artifact.id.toString(),
            fileUpload(), null, null, null, null, null, null, null, null, null, null,
            result.artifactETag, "replace-key",
        )

        assertEquals(200, response.status)
        val command = argumentCaptor<ReplaceInformationRequestEvidenceCommand>().also { verify(uploadService).replace(it.capture()) }.firstValue
        assertEquals(artifact.id, command.artifactId)
        assertEquals(CommandPrecondition.ExpectedRevision(result.artifactETag), command.precondition)
    }

    @Test
    fun `a withdrawal and a removal delegate their artifact, reason, precondition, and key`()
    {
        whenever(collectionService.withdraw(any())).thenReturn(result)
        whenever(collectionService.remove(any())).thenReturn(result)

        val withdrawn = resource.withdraw(
            requestId.toString(), requirementId.toString(), artifact.id.toString(),
            InformationRequestEvidenceStateChangeRequest("Recorded in error"), result.artifactETag, "withdraw-key",
        )
        val removed = resource.remove(
            requestId.toString(), requirementId.toString(), artifact.id.toString(),
            "No longer relevant", result.artifactETag, "remove-key",
        )

        assertEquals(200, withdrawn.status)
        assertEquals(200, removed.status)
        val withdrawal = argumentCaptor<ChangeInformationRequestEvidenceStateCommand>().also { verify(collectionService).withdraw(it.capture()) }.firstValue
        assertEquals("Recorded in error", withdrawal.reason)
        assertEquals("withdraw-key", withdrawal.idempotencyKey)
        val removal = argumentCaptor<ChangeInformationRequestEvidenceStateCommand>().also { verify(collectionService).remove(it.capture()) }.firstValue
        assertEquals("No longer relevant", removal.reason)
    }

    @Test
    fun `a list answers every visible artifact and the occurrence evidence ETag`()
    {
        whenever(queryService.list(requestId, requirementId, access))
            .thenReturn(InformationRequestEvidenceList(requirementId, "\"$requirementId:1\"", listOf(result.artifact), evaluation = null))

        val response = resource.list(requestId.toString(), requirementId.toString())

        assertEquals(200, response.status)
        assertEquals("\"$requirementId:1\"", response.getHeaderString("ETag"))
        assertEquals(listOf(artifact.id), (response.entity as InformationRequestEvidenceListDto).artifacts.map { it.id })
    }

    @Test
    fun `a download answers the content as an attachment that a browser will not run`()
    {
        val versionId = UUID.randomUUID()
        whenever(
            queryService.openContent(requestId, requirementId, artifact.id, versionId, access, InformationRequestEvidenceContentUse.DOWNLOAD),
        ).thenReturn(InformationRequestEvidenceContent(contentFile(), "collected \"record\".pdf", "application/pdf", false))

        val response = resource.download(requestId.toString(), requirementId.toString(), artifact.id.toString(), versionId.toString())

        assertEquals(200, response.status)
        assertEquals("application/pdf", response.mediaType.toString())
        assertEquals(
            "attachment; filename=\"collected _record_.pdf\"; filename*=UTF-8''collected%20%22record%22.pdf",
            response.getHeaderString("Content-Disposition"),
        )
        assertEquals("nosniff", response.getHeaderString("X-Content-Type-Options"))
        assertEquals("sandbox; default-src 'none'", response.getHeaderString("Content-Security-Policy"))
        assertEquals("no-store", response.getHeaderString("Cache-Control"))
    }

    @Test
    fun `a preview answers the content inline`()
    {
        val versionId = UUID.randomUUID()
        whenever(
            queryService.openContent(requestId, requirementId, artifact.id, versionId, access, InformationRequestEvidenceContentUse.PREVIEW),
        ).thenReturn(InformationRequestEvidenceContent(contentFile(), "collected-record.pdf", "application/pdf", true))

        val response = resource.preview(requestId.toString(), requirementId.toString(), artifact.id.toString(), versionId.toString())

        assertTrue(response.getHeaderString("Content-Disposition").startsWith("inline; "))
    }

    @Test
    fun `refusals map to stable statuses`()
    {
        whenever(queryService.list(any(), any(), any()))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Not found"))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.EVIDENCE_NOT_COLLECTED, "Not evidence"))
            .thenThrow(ForbiddenException("Denied"))

        assertEquals(404, resource.list(requestId.toString(), requirementId.toString()).status)
        assertEquals(409, resource.list(requestId.toString(), requirementId.toString()).status)
        assertEquals(403, resource.list(requestId.toString(), requirementId.toString()).status)
        assertEquals(400, resource.list("not-a-request", requirementId.toString()).status)
    }

    @Test
    fun `a no-auth call resolves its own credentials and never reaches another request`()
    {
        val participant = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext(sessionRef = "session"))
        whenever(readAccessService.resolve("access-token", "session-token")).thenReturn(InformationRequestNoAuthAccess(participant, requestId))
        whenever(readAccessService.resolve("other-token", "session-token")).thenReturn(InformationRequestNoAuthAccess(participant, UUID.randomUUID()))
        whenever(uploadService.upload(any())).thenReturn(result)

        val uploaded = noAuthResource.upload(
            requestId.toString(), requirementId.toString(), fileUpload(), null, null, null, null, null, null, null, null, null, null,
            "\"$requirementId:0\"", "upload-key", "access-token", "session-token",
        )
        assertEquals(201, uploaded.status)
        val command = argumentCaptor<UploadInformationRequestEvidenceCommand>().also { verify(uploadService).upload(it.capture()) }.firstValue
        assertEquals(participant, command.access)
        assertEquals(PrincipalKind.PARTICIPANT, command.access.principal.kind)

        assertEquals(404, noAuthResource.list(requestId.toString(), requirementId.toString(), "other-token", "session-token").status)
        assertEquals(400, noAuthResource.list(requestId.toString(), requirementId.toString(), null, "session-token").status)
        verify(queryService, never()).list(any(), any(), any())
    }

    private fun upload(
        target: InformationRequestEvidenceResource,
        file: FileUpload? = fileUpload(),
        encryptionMode: String? = null,
        issuer: String? = null,
        issuedOn: String? = null,
        expiresOn: String? = null,
        coverageStartsOn: String? = null,
        coverageEndsOn: String? = null,
        ifMatch: String? = "\"$requirementId:0\"",
        idempotencyKey: String? = "upload-key",
    ) = target.upload(
        requestId.toString(), requirementId.toString(), file, encryptionMode,
        issuer, null, null, issuedOn, expiresOn, coverageStartsOn, coverageEndsOn, null, null,
        ifMatch, idempotencyKey,
    )

    private fun fileUpload(): FileUpload
    {
        val stored = contentFile()
        val upload: FileUpload = mock()
        whenever(upload.fileName()).thenReturn("C:\\uploads\\collected-record.pdf")
        whenever(upload.contentType()).thenReturn("application/pdf")
        whenever(upload.uploadedFile()).thenReturn(stored.toPath())
        whenever(upload.size()).thenReturn(stored.length())
        return upload
    }

    private fun contentFile(): File =
        File.createTempFile("evidence-content", ".pdf").apply {
            deleteOnExit()
            writeText("%PDF-1.4\n%%EOF\n")
        }
}
