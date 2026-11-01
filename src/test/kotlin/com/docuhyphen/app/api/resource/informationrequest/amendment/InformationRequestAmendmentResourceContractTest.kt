package com.docuhyphen.app.api.resource.informationrequest.amendment

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestAmendmentDto
import com.docuhyphen.app.api.model.dto.InformationRequestAmendmentResultDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRefusalDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestAmendmentResult
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestAmendmentView
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestReadableAmendment
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.resource.informationrequest.amendment.operations.InformationRequestAmendmentResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.amendment.operations.InformationRequestNoAuthAmendmentResourceOperations
import com.docuhyphen.app.api.resource.model.AmendInformationRequestRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateValidationException
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestAmendmentResourceContractTest
{
    private val requestId = UUID.randomUUID()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val access = RequestAccessContext(principal, AuthorizationContext(sessionRef = "owner"))
    private val amendmentService: InformationRequestAmendmentService = mock()
    private val queryService: InformationRequestAmendmentQueryService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val readAccessService: InformationRequestNoAuthReadAccessService =
        InformationRequestNoAuthAccessDoubles.current()
    private val resource = InformationRequestAmendmentResource(amendmentService, queryService, accessContextFactory)
    private val noAuthResource =
        InformationRequestNoAuthAmendmentResource(amendmentService, queryService, readAccessService)

    @Test
    fun `a wildcard If-Match on an amendment is sent to the service as a missing precondition`() {
        whenever(amendmentService.amend(any()))
            .thenThrow(com.docuhyphen.app.api.service.command.CommandPreconditionException.required("\"request:2\""))

        val response =
            resource.amend(requestId.toString(), AmendInformationRequestRequest(UUID.randomUUID()), "*", "amend-key")

        assertEquals(428, response.status)
        verify(amendmentService).amend(
            org.mockito.kotlin.argThat { precondition == CommandPrecondition.Absent },
        )
    }

    private val amendment = InformationRequestAmendment().apply {
        informationRequestId = requestId
        fromTemplateVersionId = UUID.randomUUID()
        toTemplateVersionId = UUID.randomUUID()
        amendedByPrincipalKind = principal.kind
        amendedByPrincipalId = principal.id
    }
    private val visibleChange = change("recorded-item")
    private val hiddenChange = change("restricted-item")
    private val notice = InformationRequestNoticeIntent().apply {
        informationRequestId = requestId
        amendmentId = amendment.id
        partyId = UUID.randomUUID()
    }
    private val view = InformationRequestAmendmentView(amendment, listOf(visibleChange, hiddenChange), listOf(notice))
    private val readable =
        InformationRequestReadableAmendment(view, setOf(visibleChange.templateRequirementId), setOf(notice.partyId))
    private val request = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        templateVersionId = amendment.toTemplateVersionId
        ownerType = InformationRequestOwnerType.ORGANIZATION
        state = InformationRequestState.ISSUED
    }

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(queryService.readable(any(), any(), any())).thenReturn(readable)
    }

    @Test
    fun `amendments are a subordinate collection of the request on both surfaces`()
    {
        assertEquals(
            "/information-requests/{id}/amendments",
            InformationRequestAmendmentResourceOperations::class.java.getAnnotation(Path::class.java).value
        )
        assertEquals(
            "no-auth/information-requests/{id}/amendments",
            InformationRequestNoAuthAmendmentResourceOperations::class.java.getAnnotation(Path::class.java).value,
        )
    }

    @Test
    fun `an amendment delegates its target, reason, precondition, and key and answers what the caller may see`()
    {
        whenever(amendmentService.amend(any())).thenReturn(
            InformationRequestAmendmentResult(
                request,
                view,
                "\"request:3\""
            )
        )
        val target = amendment.toTemplateVersionId

        val response = resource.amend(
            requestId.toString(),
            AmendInformationRequestRequest(target, reasonCode = " revised "),
            "\"request:2\"",
            "amend-key"
        )

        assertEquals(201, response.status)
        assertEquals("\"request:3\"", response.getHeaderString("ETag"))
        val body = response.entity as InformationRequestAmendmentResultDto
        assertEquals(listOf("recorded-item"), body.amendment.changes.map { it.requirementKey })
        assertEquals(1, body.amendment.undisclosedChangeCount)
        assertEquals(
            listOf(InformationRequestNoticeDeliveryState.PENDING),
            body.amendment.notices.map { it.deliveryState })
        val command =
            argumentCaptor<AmendInformationRequestCommand>().also { verify(amendmentService).amend(it.capture()) }.firstValue
        assertEquals(target, command.targetTemplateVersionId)
        assertEquals("revised", command.reasonCode)
        assertEquals(CommandPrecondition.ExpectedRevision("\"request:2\""), command.precondition)
        assertEquals("amend-key", command.idempotencyKey)
    }

    @Test
    fun `a refused amendment keeps its stable reason and a missing key never reaches the service`()
    {
        whenever(amendmentService.amend(any())).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.AMENDMENT_SCHEMA_CHANGED,
                "Another schema"
            ),
        )

        val refused = resource.amend(
            requestId.toString(),
            AmendInformationRequestRequest(UUID.randomUUID()),
            "\"request:2\"",
            "key"
        )
        assertEquals(409, refused.status)
        assertEquals(
            InformationRequestErrorCatalog.AMENDMENT_SCHEMA_CHANGED,
            (refused.entity as ResponseError).reasonCode
        )
        assertEquals(
            400,
            resource.amend(
                requestId.toString(),
                AmendInformationRequestRequest(UUID.randomUUID()),
                "\"x\"",
                null
            ).status
        )
    }

    @Test
    fun `an unserved target Version, a missing request, and an invalid ad hoc configuration keep distinct statuses`()
    {
        whenever(amendmentService.amend(any()))
            .thenThrow(
                InformationRequestCapabilityNotInstalledException(
                    "Review is not installed",
                    listOf(InformationRequestCapabilityRequirement(InformationRequestCapability.RESPONSE_REVIEW, 1)),
                ),
            )
            .thenThrow(IllegalArgumentException("Information Request not found"))
            .thenThrow(InformationRequestTemplateValidationException("A section needs a requirement"))

        val unserved =
            resource.amend(requestId.toString(), AmendInformationRequestRequest(UUID.randomUUID()), "\"x\"", "key")
        assertEquals(409, unserved.status)
        assertEquals(
            InformationRequestErrorCatalog.CAPABILITY_NOT_INSTALLED,
            (unserved.entity as ResponseError).reasonCode
        )
        assertEquals(
            404,
            resource.amend(
                requestId.toString(),
                AmendInformationRequestRequest(UUID.randomUUID()),
                "\"x\"",
                "key"
            ).status
        )
        assertEquals(
            400,
            resource.amend(
                requestId.toString(),
                AmendInformationRequestRequest(UUID.randomUUID()),
                "\"x\"",
                "key"
            ).status
        )
    }

    @Test
    fun `an invalid private amendment identifies the section and requirement to repair`() {
        whenever(amendmentService.amend(any())).thenThrow(
            InformationRequestTemplateValidationException(
                "A document Requirement needs an evidence policy",
                sectionKey = "requested-records",
                requirementKey = "supporting-record",
            ),
        )

        val response = resource.amend(
            requestId.toString(),
            AmendInformationRequestRequest(configuration = InformationRequestTemplateConfigurationRequest()),
            "\"request:2\"",
            "invalid-private-amendment",
        )

        assertEquals(400, response.status)
        val refusal = response.entity as InformationRequestTemplateRefusalDto
        assertEquals("INFORMATION_REQUEST_TEMPLATE_INVALID", refusal.reasonCode)
        assertEquals("requested-records", refusal.sectionKey)
        assertEquals("supporting-record", refusal.requirementKey)
    }

    @Test
    fun `a respondent reads the amendments of its own link's request`()
    {
        val respondent =
            RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "link"))
        whenever(readAccessService.resolve(eq(requestId), eq("link-token"), anyOrNull())).thenReturn(respondent)
        whenever(queryService.amendments(requestId, respondent)).thenReturn(listOf(readable))

        val response = noAuthResource.list(requestId.toString(), "link-token", null)

        assertEquals(200, response.status)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(amendment.id), (response.entity as Array<InformationRequestAmendmentDto>).map { it.id })
    }

    private fun change(key: String) = InformationRequestAmendmentChange().apply {
        amendmentId = amendment.id
        informationRequestId = requestId
        templateRequirementId = UUID.randomUUID()
        requirementKey = key
        changeKind = InformationRequestAmendmentChangeKind.MEANING_CHANGED
        fromTemplateBindingId = UUID.randomUUID()
        toTemplateBindingId = UUID.randomUUID()
        reconfirmationRequired = true
    }
}
