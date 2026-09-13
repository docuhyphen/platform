package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestAccessLinkDto
import com.docuhyphen.app.api.model.dto.InformationRequestAccessLinkIssuedDto
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.resource.model.IssueInformationRequestAccessLinkRequest
import com.docuhyphen.app.api.resource.model.ReplaceInformationRequestAccessLinkRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestBootstrapShareLinkIssuance
import com.docuhyphen.app.api.service.informationrequest.InformationRequestBootstrapShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.IssueInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.service.informationrequest.ReplaceInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.informationrequest.RevokeInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.service.informationrequest.RotateInformationRequestBootstrapShareLinkCommand
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * The owner-facing bootstrap access-link resource exposes issuance, rotation, replacement, and
 * revocation of a [ShareLinkMode.VERIFICATION_BOOTSTRAP] link. It is authenticated only; the
 * respondent-facing no-auth adapter for contact proof and session issuance is a separate resource.
 */
class InformationRequestAccessLinkResourceContractTest
{
    private val bootstrapShareLinkService = mock<InformationRequestBootstrapShareLinkService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestAccessLinkResource(
        bootstrapShareLinkService,
        accessContextFactory,
    )

    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val requestId = UUID.randomUUID()
    private val partyId = UUID.randomUUID()
    private val shareLinkId = UUID.randomUUID()

    private fun shareLink(status: ShareLinkStatus = ShareLinkStatus.ACTIVE) = ShareLink().apply {
        id = shareLinkId
        shareId = UUID.randomUUID()
        tokenHash = "hash"
        linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
        this.status = status
        rotationCount = 0
    }

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `access link administration uses resource based paths and verbs`()
    {
        val resourceClass = InformationRequestAccessLinkResource::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }

        assertEquals(
            "/information-requests/{id}/access-links",
            resourceClass.getAnnotation(Path::class.java).value,
        )
        assertEquals(true, methods.getValue("issue").isAnnotationPresent(POST::class.java))
        assertEquals(false, methods.getValue("issue").isAnnotationPresent(Path::class.java))
        assertEquals("/{shareLinkId}/rotation", methods.getValue("rotate").getAnnotation(Path::class.java).value)
        assertEquals("/{shareLinkId}/replacement", methods.getValue("replace").getAnnotation(Path::class.java).value)
        assertEquals("/{shareLinkId}/revocation", methods.getValue("revoke").getAnnotation(Path::class.java).value)
    }

    @Test
    fun `issue requires an idempotency key and returns the raw token only once`()
    {
        val issuance = InformationRequestBootstrapShareLinkIssuance(shareLink(), "raw-token-value")
        whenever(bootstrapShareLinkService.issue(any())).thenReturn(issuance)
        val body = IssueInformationRequestAccessLinkRequest(partyId = partyId)

        val missingKey = resource.issue(requestId.toString(), body, "\"v1\"", null)
        val invalidId = resource.issue("not-a-uuid", body, "\"v1\"", "idem-1")
        val issued = resource.issue(requestId.toString(), body, "\"v1\"", "idem-1")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, missingKey.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalidId.status)
        assertEquals(Response.Status.CREATED.statusCode, issued.status)
        val dto = issued.entity as InformationRequestAccessLinkIssuedDto
        assertEquals("raw-token-value", dto.accessToken)
        assertEquals(shareLinkId, dto.shareLinkId)
        verify(bootstrapShareLinkService).issue(
            IssueInformationRequestBootstrapShareLinkCommand(
                requestId = requestId,
                partyId = partyId,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"v1\"")),
                idempotencyKey = "idem-1",
                expiresAt = null,
                maxUses = null,
            ),
        )
    }

    @Test
    fun `rotate delegates to the bootstrap share link service and returns the new raw token`()
    {
        val issuance = InformationRequestBootstrapShareLinkIssuance(shareLink(), "rotated-token-value")
        whenever(bootstrapShareLinkService.rotate(any())).thenReturn(issuance)

        val rotated = resource.rotate(requestId.toString(), shareLinkId.toString(), "\"v1\"", "idem-1")

        assertEquals(Response.Status.OK.statusCode, rotated.status)
        val dto = rotated.entity as InformationRequestAccessLinkIssuedDto
        assertEquals("rotated-token-value", dto.accessToken)
        verify(bootstrapShareLinkService).rotate(
            RotateInformationRequestBootstrapShareLinkCommand(
                requestId = requestId,
                shareLinkId = shareLinkId,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"v1\"")),
                idempotencyKey = "idem-1",
            ),
        )
    }

    @Test
    fun `replace delegates to the bootstrap share link service with new constraints`()
    {
        val issuance = InformationRequestBootstrapShareLinkIssuance(shareLink(), "replaced-token-value")
        whenever(bootstrapShareLinkService.replace(any())).thenReturn(issuance)
        val body = ReplaceInformationRequestAccessLinkRequest(maxUses = 5)

        val replaced = resource.replace(requestId.toString(), shareLinkId.toString(), body, "\"v1\"", "idem-1")

        assertEquals(Response.Status.CREATED.statusCode, replaced.status)
        val dto = replaced.entity as InformationRequestAccessLinkIssuedDto
        assertEquals("replaced-token-value", dto.accessToken)
        verify(bootstrapShareLinkService).replace(
            ReplaceInformationRequestBootstrapShareLinkCommand(
                requestId = requestId,
                shareLinkId = shareLinkId,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"v1\"")),
                idempotencyKey = "idem-1",
                expiresAt = null,
                maxUses = 5,
            ),
        )
    }

    @Test
    fun `revoke delegates to the bootstrap share link service and never returns a token`()
    {
        whenever(bootstrapShareLinkService.revoke(any())).thenReturn(shareLink(ShareLinkStatus.REVOKED))

        val revoked = resource.revoke(requestId.toString(), shareLinkId.toString(), "\"v1\"", "idem-1")

        assertEquals(Response.Status.OK.statusCode, revoked.status)
        val dto = revoked.entity as InformationRequestAccessLinkDto
        assertEquals(ShareLinkStatus.REVOKED, dto.status)
        verify(bootstrapShareLinkService).revoke(
            RevokeInformationRequestBootstrapShareLinkCommand(
                requestId = requestId,
                shareLinkId = shareLinkId,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"v1\"")),
                idempotencyKey = "idem-1",
            ),
        )
    }

    @Test
    fun `domain failures map to stable response statuses`()
    {
        whenever(bootstrapShareLinkService.issue(any()))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED, "Sign-in required"))
            .thenThrow(IllegalArgumentException("Information Request not found"))
            .thenThrow(ForbiddenException("Denied"))
        val body = IssueInformationRequestAccessLinkRequest(partyId = partyId)

        assertMapped(body, Response.Status.CONFLICT, "Sign-in required")
        assertMapped(body, Response.Status.NOT_FOUND, "Information Request not found")
        assertMapped(body, Response.Status.FORBIDDEN, "Denied")
    }

    private fun assertMapped(
        body: IssueInformationRequestAccessLinkRequest,
        status: Response.Status,
        message: String,
    )
    {
        val response = resource.issue(requestId.toString(), body, "\"v1\"", "idem-1")
        assertEquals(status.statusCode, response.status)
        assertEquals(message, (response.entity as ResponseError).errorMessage)
    }
}
