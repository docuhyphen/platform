package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceCapturedAttribute
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidencePolicy
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRecurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestRefreshRuleRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.resource.model.DefineInformationRequestRecurrenceRequest
import com.docuhyphen.app.api.resource.model.DefineInformationRequestRefreshRuleRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidencePolicyLoader
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestFollowUpService
import com.docuhyphen.app.api.service.informationrequest.party.InMemoryReceiptStore
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Instant
import java.util.*

class InformationRequestFollowUpScheduleBoundsTest {
    private val request = InformationRequest().apply {
        id = UUID.randomUUID()
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        state = InformationRequestState.ISSUED
        aggregateRevision = 4
    }
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "coordinator"))
    private val gate = mock<InformationRequestMutationGate>().also { gate ->
        whenever(gate.lock(request.id)).thenAnswer {
            LockedInformationRequest(
                Exchange().apply {
                    id = request.exchangeId
                    status = ExchangeStatus.ACCEPTED_STARTED
                    isDeleted = false
                },
                request,
            )
        }
    }
    private val recurrences = mock<InformationRequestRecurrenceRepository>().also {
        whenever(it.save(any())).thenAnswer { invocation -> invocation.getArgument<InformationRequestRecurrence>(0) }
    }
    private val refreshRules = mock<InformationRequestRefreshRuleRepository>().also {
        whenever(it.save(any())).thenAnswer { invocation -> invocation.getArgument<InformationRequestRefreshRule>(0) }
    }
    private val requestRepository = mock<InformationRequestRepository>().also {
        whenever(it.update(any())).thenAnswer { invocation -> invocation.getArgument<InformationRequest>(0) }
    }
    private val receipts = InMemoryReceiptStore()
    private val followUps = InformationRequestFollowUpService(
        gate = gate,
        successorService = mock(),
        recurrenceRepository = recurrences,
        refreshRuleRepository = refreshRules,
        lineageRepository = mock(),
        requirementRepository = documentRequirements(),
        templateRequirementRepository = mock<InformationRequestTemplateRequirementRepository>().also {
            whenever(it.findById(DOCUMENT_TEMPLATE_REQUIREMENT_ID)).thenReturn(
                InformationRequestTemplateRequirement().apply {
                    id = DOCUMENT_TEMPLATE_REQUIREMENT_ID
                    requirementKey = DOCUMENT_KEY
                    requirementType = InformationRequestRequirementType.DOCUMENT
                },
            )
        },
        commandReceiptService = CommandReceiptService(receipts, CommandReceiptScope()),
        transitionHistory = mock(),
        evidencePolicies = mock<InformationRequestEvidencePolicyLoader>().also {
            val policy = mock<InformationRequestEvidencePolicy>()
            whenever(policy.attributeRequirements).thenReturn(
                mapOf(InformationRequestEvidenceCapturedAttribute.EXPIRY_DATE to InformationRequestEvidenceAttributeRequirement.REQUIRED),
            )
            whenever(it.forBinding(DOCUMENT_BINDING_ID)).thenReturn(policy)
        },
        clock = Clock.systemUTC(),
        requestRepository = requestRepository,
    )
    private val accessContexts = mock<InformationRequestAccessContextFactory> {
        on { currentAuthenticated() } doReturn access
    }
    private val recurrenceResource = InformationRequestRecurrenceResource(followUps, accessContexts, mock())
    private val refreshRuleResource = InformationRequestRefreshRuleResource(followUps, accessContexts, mock())

    @Test
    fun `a recurrence outside its bounds is answered 400 with the schedule reason before anything is written`() {
        val outOfBounds = mapOf(
            "interval count zero" to schedule(intervalCount = 0),
            "negative interval count" to schedule(intervalCount = -1),
            "interval count above one thousand" to schedule(intervalCount = 1001),
            "smallest interval count" to schedule(intervalCount = Int.MIN_VALUE),
            "no occurrences" to schedule(maximumOccurrences = 0),
            "negative occurrences" to schedule(maximumOccurrences = -3),
            "first due before 1970" to schedule(firstDueAt = "1969-12-31T23:59:59.999Z"),
            "first due in the year 10000" to schedule(firstDueAt = "+10000-01-01T00:00:00Z"),
            "first due beyond the stored range" to schedule(firstDueAt = "+300000-01-01T00:00:00Z"),
        )

        outOfBounds.forEach { (name, body) ->
            val response = recurrenceResource.define(
                request.id.toString(),
                body,
                InformationRequestETag.aggregateOf(request),
                "recurrence-$name"
            )

            assertRefused(name, response)
        }
        verify(recurrences, never()).save(any())
        verify(requestRepository, never()).update(any())
        assertEquals(4, request.aggregateRevision)
    }

    @Test
    fun `a recurrence at the edges of its bounds is recorded as stated`() {
        val inBounds = listOf(
            schedule(intervalCount = 1, maximumOccurrences = null, firstDueAt = "1970-01-01T00:00:00Z"),
            schedule(intervalCount = 1000, maximumOccurrences = 1, firstDueAt = "9999-12-31T23:59:59.999Z"),
        )

        inBounds.forEachIndexed { index, body ->
            val response = recurrenceResource.define(
                request.id.toString(),
                body,
                InformationRequestETag.aggregateOf(request),
                "recurrence-edge-$index"
            )

            assertEquals(Response.Status.CREATED.statusCode, response.status, "$body")
            assertEquals(InformationRequestETag.aggregateOf(request), response.getHeaderString(HttpHeaders.ETAG))
        }
        val saved = argumentCaptor<InformationRequestRecurrence>()
        verify(recurrences, times(2)).save(saved.capture())
        assertEquals(
            listOf(
                Triple(1, null, Instant.parse("1970-01-01T00:00:00Z")),
                Triple(1000, 1, Instant.parse("9999-12-31T23:59:59.999Z")),
            ),
            saved.allValues.map { Triple(it.intervalCount, it.maximumOccurrences, it.firstDueAt.toInstant()) },
        )
    }

    @Test
    fun `a refresh rule with a negative lead time is answered 400 with the schedule reason before anything is written`() {
        listOf(-1, Int.MIN_VALUE).forEach { leadDays ->
            val response = refreshRuleResource.define(
                request.id.toString(),
                DefineInformationRequestRefreshRuleRequest(DOCUMENT_KEY, leadDays),
                InformationRequestETag.aggregateOf(request),
                "refresh-rule-$leadDays",
            )

            assertRefused("lead days $leadDays", response)
        }
        verify(refreshRules, never()).save(any())
        verify(requestRepository, never()).update(any())
        assertEquals(4, request.aggregateRevision)
    }

    @Test
    fun `a refresh rule with no lead time or the longest lead time is recorded as stated`() {
        listOf(0, Int.MAX_VALUE).forEach { leadDays ->
            val response = refreshRuleResource.define(
                request.id.toString(),
                DefineInformationRequestRefreshRuleRequest(DOCUMENT_KEY, leadDays),
                InformationRequestETag.aggregateOf(request),
                "refresh-rule-$leadDays",
            )

            assertEquals(Response.Status.CREATED.statusCode, response.status, "lead days $leadDays")
        }
        val saved = argumentCaptor<InformationRequestRefreshRule>()
        verify(refreshRules, times(2)).save(saved.capture())
        assertEquals(listOf(0, Int.MAX_VALUE), saved.allValues.map { it.leadDays })
    }

    private fun assertRefused(name: String, response: Response) {
        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status, name)
        assertEquals(
            InformationRequestErrorCatalog.FOLLOW_UP_SCHEDULE_INVALID,
            (response.entity as ResponseError).reasonCode,
            name
        )
    }

    private fun schedule(
        intervalCount: Int = 1,
        maximumOccurrences: Int? = 3,
        firstDueAt: String = "2027-01-04T09:00:00Z",
    ) = DefineInformationRequestRecurrenceRequest(
        intervalUnit = InformationRequestRecurrenceUnit.MONTH,
        intervalCount = intervalCount,
        firstDueAt = firstDueAt,
        maximumOccurrences = maximumOccurrences,
    )

    private fun documentRequirements(): InformationRequestRequirementRepository =
        mock<InformationRequestRequirementRepository>().also {
            whenever(it.findForRequest(request.id)).thenReturn(
                listOf(
                    InformationRequestRequirement().apply {
                        id = UUID.randomUUID()
                        informationRequestId = request.id
                        sourceTemplateVersionId = request.templateVersionId
                        sourceTemplateRequirementId = DOCUMENT_TEMPLATE_REQUIREMENT_ID
                        sourceTemplateBindingId = DOCUMENT_BINDING_ID
                        occurrencePath = "root"
                    },
                ),
            )
        }

    private companion object {
        const val DOCUMENT_KEY = "periodic-record"
        val DOCUMENT_TEMPLATE_REQUIREMENT_ID: UUID = UUID.randomUUID()
        val DOCUMENT_BINDING_ID: UUID = UUID.randomUUID()
    }
}
