package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.InformationRequestExecutionStandingDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestStandingReason
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestHeaderProjectionServiceTest {
    private val authorizationService = mock<AuthorizationService>()
    private val standingService = mock<InformationRequestExecutionStandingService>()
    private val service = InformationRequestHeaderProjectionService(authorizationService, standingService)
    private val lapse = InformationRequestExecutionStanding(
        InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
        InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE,
    )
    private val request = InformationRequest().apply {
        id = UUID.randomUUID()
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        ownerUserId = UUID.randomUUID()
    }

    @BeforeEach
    fun standing() {
        whenever(standingService.standingFor(request)).thenReturn(lapse)
    }

    @Test
    fun `a manager sees why the request continues after the owner's lapse and a participant only that it continues`() {
        val manager = RequestAccessContext(
            PrincipalRef.user(UUID.randomUUID()),
            AuthorizationContext(activeOrgId = request.ownerOrganizationId)
        )
        val participant =
            RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)
        managesRequest(manager, true)
        managesRequest(participant, false)

        assertEquals(
            InformationRequestExecutionStandingDto(
                InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
                InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE,
            ),
            service.headerOf(request, manager).executionStanding,
        )
        assertEquals(
            InformationRequestExecutionStandingDto(InformationRequestExecutionStandingKind.ACTIVE),
            service.headerOf(request, participant).executionStanding,
        )
    }

    @Test
    fun `a caller who does not manage the request sees its Template Version without owner identifiers`() {
        val participant =
            RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)
        managesRequest(participant, false)

        val header = service.headerOf(request, participant)

        assertNull(header.ownerOrganizationId)
        assertNull(header.ownerUserId)
        assertEquals(request.templateVersionId, header.templateVersionId)
        assertEquals(request.id, header.id)
        assertEquals(InformationRequestOwnerType.ORGANIZATION, header.ownerType)
        assertEquals(InformationRequestETag.aggregateOf(request), header.requestETag)
    }

    @Test
    fun `a caller who manages the request is shown its owner and Template identifiers`() {
        val manager = RequestAccessContext(
            PrincipalRef.user(UUID.randomUUID()),
            AuthorizationContext(activeOrgId = request.ownerOrganizationId)
        )
        managesRequest(manager, true)

        val header = service.headerOf(request, manager)

        assertEquals(request.ownerOrganizationId, header.ownerOrganizationId)
        assertEquals(request.ownerUserId, header.ownerUserId)
        assertEquals(request.templateVersionId, header.templateVersionId)
    }

    @Test
    fun `a superseded request names its replacement only to a caller who may view the replacement`() {
        val replacementId = UUID.randomUUID()
        request.supersededByRequestId = replacementId
        val viewer = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)
        val outsider = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)
        managesRequest(viewer, true)
        managesRequest(outsider, true)
        viewsRequest(viewer, replacementId, true)
        viewsRequest(outsider, replacementId, false)

        assertEquals(replacementId, service.headerOf(request, viewer).supersededByRequestId)
        assertNull(service.headerOf(request, outsider).supersededByRequestId)
        assertEquals(request.ownerOrganizationId, service.headerOf(request, outsider).ownerOrganizationId)
    }

    private fun viewsRequest(access: RequestAccessContext, requestId: UUID, views: Boolean) {
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                access.authorization,
            ),
        ).thenReturn(if (views) Decision.Allow() else Decision.Deny(Decision.REASON_NO_GRANT, "not a viewer"))
    }

    private fun managesRequest(access: RequestAccessContext, manages: Boolean) {
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
                ResourceRef.informationRequest(request.id),
                access.authorization,
            ),
        ).thenReturn(if (manages) Decision.Allow() else Decision.Deny(Decision.REASON_NO_GRANT, "not a manager"))
    }
}
