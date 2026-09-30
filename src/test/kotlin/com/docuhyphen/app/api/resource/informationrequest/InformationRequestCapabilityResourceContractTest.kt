package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestCapabilitiesDto
import com.docuhyphen.app.api.model.informationrequest.InformationRequestCapabilities
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOwnerStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestStandingReason
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCapabilityService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestCapabilityResourceContractTest
{
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val capabilityService = mock<InformationRequestCapabilityService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestCapabilityResource(capabilityService, accessContextFactory)

    @Test
    fun `the caller's Information Request capabilities are read as one resource`()
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(capabilityService.forCaller(access)).thenReturn(
            InformationRequestCapabilities(
                scope = InformationRequestOwnerStanding(
                    ownerType = SubscriptionOwnerType.USER,
                    ownerId = access.principal.id,
                    planCode = PlanCode.FREE,
                    status = SubscriptionStatus.ACTIVE,
                    enforcementMode = SubscriptionEnforcementMode.ENFORCE,
                    featureIncluded = false,
                    operationallySuspended = false,
                    newWorkUnavailableReason = InformationRequestStandingReason.FEATURE_NOT_INCLUDED,
                ),
                typedAnswersAvailable = true,
                personalTemplatesAvailable = false,
                assignedWork = true,
                holdsRequests = false,
            ),
        )

        val response = resource.get()

        assertEquals("/information-request-capabilities", InformationRequestCapabilityResource::class.java.getAnnotation(Path::class.java).value)
        assertTrue(InformationRequestCapabilityResource::class.java.declaredMethods.single { it.name == "get" }.isAnnotationPresent(GET::class.java))
        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as InformationRequestCapabilitiesDto
        assertEquals(SubscriptionOwnerType.USER, body.ownerType)
        assertEquals(PlanCode.FREE, body.planCode)
        assertEquals(false, body.newWorkAvailable)
        assertEquals(InformationRequestStandingReason.FEATURE_NOT_INCLUDED, body.newWorkUnavailableReason)
        assertEquals(true, body.assignedWork)
    }

    @Test
    fun `an unauthenticated caller is refused`()
    {
        whenever(accessContextFactory.currentAuthenticated()).thenThrow(ForbiddenException("Not authenticated"))

        assertEquals(Response.Status.FORBIDDEN.statusCode, resource.get().status)
    }
}
