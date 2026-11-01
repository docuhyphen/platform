package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestParticipantAccountUpgradeDto
import com.docuhyphen.app.api.model.entity.ParticipantAccountLink
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestParticipantAccountUpgrade
import com.docuhyphen.app.api.model.informationrequest.noauth.UpgradeInformationRequestParticipantAccountCommand
import com.docuhyphen.app.api.resource.informationrequest.noauth.operations.InformationRequestParticipantAccountLinkResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpgradeInformationRequestParticipantAccountRequest
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestParticipantAccountUpgradeService
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
        val resourceClass = InformationRequestParticipantAccountLinkResourceOperations::class.java
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
        assertEquals(upgrade.partyETag, upgraded.getHeaderString("ETag"))
        assertEquals(upgrade.accountRespondentKey, dto.respondentKey)
        verify(upgradeService).upgrade(
            UpgradeInformationRequestParticipantAccountCommand(
                requestId = requestId,
                sessionId = sessionId,
                sessionCredential = "session-secret",
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"v1\"")),
                idempotencyKey = "idem-1",
            ),
        )
    }

    @Test
    fun `the service refusal of a session credential for another session maps to forbidden`() {
        whenever(upgradeService.upgrade(any()))
            .thenThrow(ForbiddenException("The session credential does not match the named session"))
        val body = UpgradeInformationRequestParticipantAccountRequest(sessionId = sessionId)

        val response = resource.upgrade(requestId.toString(), body, "\"v1\"", "idem-1", "other-secret")

        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
        assertEquals(
            "The session credential does not match the named session",
            (response.entity as ResponseError).errorMessage,
        )
    }

    @Test
    fun `an unusable session credential maps to a stated conflict`()
    {
        whenever(upgradeService.upgrade(any())).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
                "A verified request session credential is required",
            ),
        )
        val body = UpgradeInformationRequestParticipantAccountRequest(sessionId = sessionId)

        val response = resource.upgrade(requestId.toString(), body, "\"v1\"", "idem-1", "not-a-credential")

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals(
            InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
            (response.entity as ResponseError).reasonCode
        )
    }

    @Test
    fun `the service refusal of a caller who is not an account holder maps to forbidden`() {
        val participantAccess =
            RequestAccessContext(PrincipalRef(PrincipalKind.PARTICIPANT, UUID.randomUUID()), AuthorizationContext())
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(participantAccess)
        whenever(upgradeService.upgrade(any())).thenThrow(ForbiddenException("Only a signed-in account holder can complete an upgrade"))
        val body = UpgradeInformationRequestParticipantAccountRequest(sessionId = sessionId)

        val response = resource.upgrade(requestId.toString(), body, "\"v1\"", "idem-1")

        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
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
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Not found"))
            .thenThrow(IllegalArgumentException("Information Request not found"))
            .thenThrow(ForbiddenException("Denied"))
        val body = UpgradeInformationRequestParticipantAccountRequest(sessionId = sessionId)

        assertMapped(body, Response.Status.CONFLICT, "Email mismatch")
        assertMapped(body, Response.Status.NOT_FOUND, "Not found")
        assertMapped(body, Response.Status.NOT_FOUND, "Information Request not found")
        assertMapped(body, Response.Status.FORBIDDEN, "Denied")
    }

    @Test
    fun `an owner's suspension is rethrown for the subscription refusal mapper`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED,
                planCode = PlanCode.BUSINESS,
                ownerType = SubscriptionOwnerType.ORGANIZATION,
                message = "The owner's subscription is suspended",
            ),
        )
        whenever(upgradeService.upgrade(any())).thenThrow(denial)
        val body = UpgradeInformationRequestParticipantAccountRequest(sessionId = sessionId)

        assertSame(
            denial,
            assertThrows<SubscriptionDenialException> {
                resource.upgrade(
                    requestId.toString(),
                    body,
                    "\"v1\"",
                    "idem-1"
                )
            },
        )
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
        return InformationRequestParticipantAccountUpgrade(link, share, "\"v2\"", "linked-account-respondent")
    }
}
