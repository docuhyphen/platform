package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestAccessLinkDto
import com.docuhyphen.app.api.model.dto.InformationRequestAccessLinkIssuedDto
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.noauth.*
import com.docuhyphen.app.api.resource.informationrequest.noauth.operations.InformationRequestAccessLinkResourceOperations
import com.docuhyphen.app.api.resource.model.IssueInformationRequestAccessLinkRequest
import com.docuhyphen.app.api.resource.model.ReplaceInformationRequestAccessLinkRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
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
import java.util.*

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
    fun `the author lists access links by party without any secret`()
    {
        whenever(bootstrapShareLinkService.links(requestId, access)).thenReturn(
            listOf(
                com.docuhyphen.app.api.model.informationrequest.party.InformationRequestAccessLinkView(
                    shareLink(),
                    partyId
                )
            ),
        )

        val listed = resource.list(requestId.toString())
        val invalid = resource.list("not-a-request")

        assertEquals(
            true,
            InformationRequestAccessLinkResourceOperations::class.java.declaredMethods.single { it.name == "list" }
                .isAnnotationPresent(jakarta.ws.rs.GET::class.java))
        assertEquals(Response.Status.OK.statusCode, listed.status)
        @Suppress("UNCHECKED_CAST")
        val link = (listed.entity as Array<InformationRequestAccessLinkDto>).single()
        assertEquals(shareLinkId, link.shareLinkId)
        assertEquals(partyId, link.partyId)
        assertEquals(ShareLinkStatus.ACTIVE, link.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalid.status)
    }

    @Test
    fun `access link administration uses resource based paths and verbs`()
    {
        val resourceClass = InformationRequestAccessLinkResourceOperations::class.java
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
        val issuance = InformationRequestBootstrapShareLinkIssuance(shareLink(), "raw-token-value", "\"v2\"")
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
        assertEquals("\"v2\"", dto.partyETag)
        assertEquals("\"v2\"", issued.getHeaderString("ETag"))
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
        val issuance = InformationRequestBootstrapShareLinkIssuance(shareLink(), "rotated-token-value", "\"v2\"")
        whenever(bootstrapShareLinkService.rotate(any())).thenReturn(issuance)

        val rotated = resource.rotate(requestId.toString(), shareLinkId.toString(), "\"v1\"", "idem-1")

        assertEquals(Response.Status.OK.statusCode, rotated.status)
        val dto = rotated.entity as InformationRequestAccessLinkIssuedDto
        assertEquals("rotated-token-value", dto.accessToken)
        assertEquals("\"v2\"", rotated.getHeaderString("ETag"))
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
        val issuance = InformationRequestBootstrapShareLinkIssuance(shareLink(), "replaced-token-value", "\"v2\"")
        whenever(bootstrapShareLinkService.replace(any())).thenReturn(issuance)
        val body = ReplaceInformationRequestAccessLinkRequest(maxUses = 5)

        val replaced = resource.replace(requestId.toString(), shareLinkId.toString(), body, "\"v1\"", "idem-1")

        assertEquals(Response.Status.CREATED.statusCode, replaced.status)
        val dto = replaced.entity as InformationRequestAccessLinkIssuedDto
        assertEquals("replaced-token-value", dto.accessToken)
        assertEquals("\"v2\"", replaced.getHeaderString("ETag"))
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
    fun `every response that carries a raw access token forbids storing it`() {
        whenever(bootstrapShareLinkService.issue(any())).thenReturn(
            InformationRequestBootstrapShareLinkIssuance(
                shareLink(),
                "issued-token",
                "\"v2\""
            )
        )
        whenever(bootstrapShareLinkService.rotate(any())).thenReturn(
            InformationRequestBootstrapShareLinkIssuance(
                shareLink(),
                "rotated-token",
                "\"v2\""
            )
        )
        whenever(bootstrapShareLinkService.replace(any())).thenReturn(
            InformationRequestBootstrapShareLinkIssuance(
                shareLink(),
                "replaced-token",
                "\"v2\""
            )
        )

        val responses = listOf(
            resource.issue(
                requestId.toString(),
                IssueInformationRequestAccessLinkRequest(partyId = partyId),
                "\"v1\"",
                "idem-issue"
            ),
            resource.rotate(requestId.toString(), shareLinkId.toString(), "\"v1\"", "idem-rotate"),
            resource.replace(
                requestId.toString(),
                shareLinkId.toString(),
                ReplaceInformationRequestAccessLinkRequest(maxUses = 5),
                "\"v1\"",
                "idem-replace"
            ),
        )

        assertEquals(listOf("no-store", "no-store", "no-store"), responses.map { it.getHeaderString("Cache-Control") })
    }

    @Test
    fun `revoke delegates to the bootstrap share link service and never returns a token`()
    {
        whenever(bootstrapShareLinkService.revoke(any()))
            .thenReturn(InformationRequestBootstrapShareLinkRevocation(shareLink(ShareLinkStatus.REVOKED), "\"v3\""))

        val revoked = resource.revoke(requestId.toString(), shareLinkId.toString(), "\"v1\"", "idem-1")

        assertEquals(Response.Status.OK.statusCode, revoked.status)
        val dto = revoked.entity as InformationRequestAccessLinkDto
        assertEquals(ShareLinkStatus.REVOKED, dto.status)
        assertEquals("\"v3\"", dto.partyETag)
        assertEquals("\"v3\"", revoked.getHeaderString("ETag"))
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
    fun `invalid link limits are bad requests with a stable reason`() {
        whenever(bootstrapShareLinkService.issue(any())).thenThrow(
            InformationRequestCommandRequestException(
                "An access link must expire in the future and allow at least one use",
                InformationRequestErrorCatalog.ACCESS_LINK_LIMITS_INVALID,
            ),
        )

        val refused = resource.issue(
            requestId.toString(),
            IssueInformationRequestAccessLinkRequest(partyId = partyId, maxUses = 0),
            "\"v1\"",
            "idem-1"
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, refused.status)
        assertEquals(
            InformationRequestErrorCatalog.ACCESS_LINK_LIMITS_INVALID,
            (refused.entity as ResponseError).reasonCode
        )
    }

    @Test
    fun `domain failures map to stable response statuses`()
    {
        whenever(bootstrapShareLinkService.issue(any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED,
                    "Sign-in required"
                )
            )
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
