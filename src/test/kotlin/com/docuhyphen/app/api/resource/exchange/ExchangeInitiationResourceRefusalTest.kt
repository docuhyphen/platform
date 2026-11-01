package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.resource.model.ExchangeInitiationDto
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import com.docuhyphen.app.api.service.subscription.*
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExchangeInitiationResourceRefusalTest {
    private val initiationService = mock<ExchangeInitiationService>()
    private val resource = ExchangeResource(
        exchangeDocumentService = mock<ExchangeDocumentService>(),
        exchangeInitiationService = initiationService,
        exchangeRetrievalService = mock<ExchangeRetrievalService>(),
        exchangeUpdateService = mock<ExchangeUpdateService>(),
        exchangeParticipantService = mock<ExchangeParticipantService>(),
        shareQueryService = mock<ShareQueryService>(),
        sessionAccessManagementService = mock<ExchangeAccessManagementService>(),
        exchangeDetailsViewService = mock<ExchangeDetailsViewService>(),
        exchangeWorkflowStatusService = mock<ExchangeWorkflowStatusService>(),
    )
    private val request = ExchangeInitiationDto(name = "Collection")

    @Test
    fun `an unavailable Template Version behind the Blueprint is a conflict that names its reason`() {
        val codes = listOf(
            InformationRequestTemplateVersionUnavailableException.RETIRED,
            InformationRequestTemplateVersionUnavailableException.NOT_PUBLISHED,
            InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
            InformationRequestTemplateVersionUnavailableException.OWNER_MISMATCH,
        )

        assertAll(
            codes.map { code ->
                {
                    doThrow(InformationRequestTemplateVersionUnavailableException(code, "Refused for $code"))
                        .whenever(initiationService).initiateExchange(request)

                    assertRefusal(
                        resource.initiateExchange(request),
                        Response.Status.CONFLICT,
                        "Refused for $code",
                        code
                    )
                }
            },
        )
    }

    @Test
    fun `a lifecycle refusal of the Blueprint's request is a conflict that names its reason`() {
        whenever(initiationService.initiateExchange(request)).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
                "Information Request draft creation is not allowed",
            ),
        )

        assertRefusal(
            resource.initiateExchange(request),
            Response.Status.CONFLICT,
            "Information Request draft creation is not allowed",
            InformationRequestErrorCatalog.PARENT_STATE_INVALID,
        )
    }

    @Test
    fun `a runtime capability the Blueprint's request needs but this deployment lacks is a conflict`() {
        whenever(initiationService.initiateExchange(request)).thenThrow(
            InformationRequestCapabilityNotInstalledException("The request needs a capability", emptyList()),
        )

        assertRefusal(
            resource.initiateExchange(request),
            Response.Status.CONFLICT,
            "The request needs a capability",
            InformationRequestErrorCatalog.CAPABILITY_NOT_INSTALLED,
        )
    }

    @Test
    fun `a plan refusal is left to the subscription refusal mapper`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
                planCode = PlanCode.FREE,
                ownerType = SubscriptionOwnerType.USER,
                feature = PlanFeature.INFORMATION_REQUESTS,
                message = "Information Requests are not included in this plan",
            ),
        )
        whenever(initiationService.initiateExchange(request)).thenThrow(denial)

        val rethrown = assertThrows<SubscriptionDenialException> { resource.initiateExchange(request) }

        assertEquals(denial, rethrown)
    }

    @Test
    fun `a missing document is still a bad request`() {
        whenever(initiationService.initiateExchange(request))
            .thenThrow(IllegalArgumentException("Add at least one document"))

        assertRefusal(
            resource.initiateExchange(request),
            Response.Status.BAD_REQUEST,
            "Add at least one document",
            null
        )
    }

    private fun assertRefusal(response: Response, status: Response.Status, message: String, reasonCode: String?) {
        val error = response.entity as ResponseError
        assertEquals(status.statusCode, response.status)
        assertEquals(message, error.errorMessage)
        assertEquals(reasonCode, error.reasonCode)
    }
}
