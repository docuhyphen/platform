package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestParticipantAccountUpgradeDto
import com.docuhyphen.app.api.model.entity.ParticipantAccountLink
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpgradeInformationRequestParticipantAccountRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.model.informationrequest.InformationRequestParticipantAccountUpgrade
import com.docuhyphen.app.api.service.informationrequest.InformationRequestParticipantAccountUpgradeService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.UpgradeInformationRequestParticipantAccountCommand
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
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The authenticated, respondent-facing resource that completes a Participant's registration upgrade
 * to their own App User account. The caller's App User identity comes only from their authenticated
 * access token; the no-auth session id proving contact with the request party comes from the request
 * body, never from the token.
 */
class InformationRequestParticipantAccountLinkResourceContractTest
{
    private val upgradeService = mock<InformationRequestParticipantAccountUpgradeService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestParticipantAccountLinkResource(
        upgradeService,
        accessContextFactory,
    )

    private val appUserId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.user(appUserId), AuthorizationContext())
    private val requestId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `resource exposes a plural sub-resource path with a single POST verb`()
    {
        val resourceClass = InformationRequestParticipantAccountLinkResource::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }

        assertEquals(
            "/information-requests/{id}/participant-account-links",
            resourceClass.getAnnotation(Path::class.java).value,
        )
        assertEquals(true, methods.getValue("upgrade").isAnnotationPresent(POST::class.java))
    }

    @Test
    fun `upgrade requires an idempotency key and delegates to the upgrade service`()
    {
        val upgrade = upgradeResult()
        whenever(upgradeService.upgrade(any())).thenReturn(upgrade)
        val body = UpgradeInformationRequestParticipantAccountRequest(sessionId = sessionId)

        val missingKey = resource.upgrade(requestId.toString(), body, "\"v1\"", null)
        val invalidId = resource.upgrade("not-a-uuid", body, "\"v1\"", "idem-1")
        val upgraded = resource.upgrade(requestId.toString(), body, "\"v1\"", "idem-1", "session-secret")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, missingKey.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalidId.status)
        assertEquals(Response.Status.CREATED.statusCode, upgraded.status)
        val dto = upgraded.entity as InformationRequestParticipantAccountUpgradeDto
        assertEquals(upgrade.participantAccountLink.id, dto.participantAccountLinkId)
        assertEquals(appUserId, dto.appUserId)
        verify(upgradeService).upgrade(
            UpgradeInformationRequestParticipantAccountCommand(
                requestId = requestId,
                sessionId = sessionId,
                appUserId = appUserId,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"v1\"")),
                idempotencyKey = "idem-1",
                sessionToken = "session-secret",
            ),
        )
    }

    @Test
    fun `upgrade refuses a caller whose principal is not an App User`()
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(
            RequestAccessContext(PrincipalRef(PrincipalKind.PARTICIPANT, UUID.randomUUID()), AuthorizationContext()),
        )
        val body = UpgradeInformationRequestParticipantAccountRequest(sessionId = sessionId)

        val response = resource.upgrade(requestId.toString(), body, "\"v1\"", "idem-1")

        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
        verify(upgradeService, org.mockito.kotlin.never()).upgrade(any())
    }

    @Test
    fun `domain failures map to stable response statuses`()
    {
        whenever(upgradeService.upgrade(any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_EMAIL_MISMATCH,
                    "Email mismatch",
                ),
            )
            .thenThrow(IllegalArgumentException("Information Request not found"))
            .thenThrow(ForbiddenException("Denied"))
        val body = UpgradeInformationRequestParticipantAccountRequest(sessionId = sessionId)

        assertMapped(body, Response.Status.CONFLICT, "Email mismatch")
        assertMapped(body, Response.Status.NOT_FOUND, "Information Request not found")
        assertMapped(body, Response.Status.FORBIDDEN, "Denied")
    }

    private fun assertMapped(
        body: UpgradeInformationRequestParticipantAccountRequest,
        status: Response.Status,
        message: String,
    )
    {
        val response = resource.upgrade(requestId.toString(), body, "\"v1\"", "idem-1")
        assertEquals(status.statusCode, response.status)
        assertEquals(message, (response.entity as ResponseError).errorMessage)
    }

    private fun upgradeResult(): InformationRequestParticipantAccountUpgrade
    {
        val link = ParticipantAccountLink().apply {
            id = UUID.randomUUID()
            participantId = UUID.randomUUID()
            this.appUserId = this@InformationRequestParticipantAccountLinkResourceContractTest.appUserId
            linkedViaInformationRequestId = requestId
            linkedViaShareLinkId = UUID.randomUUID()
            linkedAt = Timestamp.from(Instant.now())
        }
        val share = Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST
            resourceId = requestId
            roleName = "CONTRIBUTOR"
        }
        return InformationRequestParticipantAccountUpgrade(link, share)
    }
}
