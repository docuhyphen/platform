package com.docuhyphen.app.api.resource.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.dto.InformationRequestAcceptedFactDto
import com.docuhyphen.app.api.model.dto.InformationRequestAcceptedFactOfferDto
import com.docuhyphen.app.api.model.dto.InformationRequestBusinessDecisionDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.*
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestAcceptedFactOfferResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestAcceptedFactResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestBusinessDecisionResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestNoAuthAcceptedFactOfferResourceOperations
import com.docuhyphen.app.api.resource.model.PromoteInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestBusinessDecisionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.RevokeInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.ws.rs.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestAcceptedFactResourceContractTest
{
    private val requestId = UUID.randomUUID()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val access = RequestAccessContext(principal, AuthorizationContext(sessionRef = "user-session"))
    private val noAuthAccess = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "link"))

    private val facts: InformationRequestAcceptedFactService = mock()
    private val factQueries: InformationRequestAcceptedFactQueryService = mock()
    private val decisions: InformationRequestBusinessDecisionService = mock()
    private val accessContextFactory: InformationRequestAccessContextFactory = mock()
    private val readAccessService: InformationRequestNoAuthReadAccessService =
        InformationRequestNoAuthAccessDoubles.current()

    private val fact = InformationRequestAcceptedFact().apply {
        subjectIdentityRefId = UUID.randomUUID()
        purposeKey = "process.eligibility"
        policyBasisKey = "policy.reuse"
        fieldDefinitionId = UUID.randomUUID()
        valueType = FieldValueType.SHORT_TEXT
        canonicalValue = "\"Synthetic value\""
        sourceInformationRequestId = requestId
        sourcePackageId = UUID.randomUUID()
        sourceSubmissionItemId = UUID.randomUUID()
        sourceRequirementId = UUID.randomUUID()
        sourceResponseId = UUID.randomUUID()
        sourceFieldValueRevisionId = UUID.randomUUID()
        sourceReviewId = UUID.randomUUID()
        confidence = InformationRequestAcceptedFactConfidence.REVIEWED
        promotedByPrincipalKind = PrincipalKind.USER
        promotedByPrincipalId = principal.id
    }
    private val view = InformationRequestAcceptedFactView(fact, null, null, InformationRequestAcceptedFactFreshness.CURRENT)
    private val decision = InformationRequestBusinessDecision().apply {
        informationRequestId = requestId
        owningProcessKey = "process.intake"
        outcomeCode = "outcome.accepted"
        kind = InformationRequestBusinessDecisionKind.RECONSIDERATION
        priorDecisionId = UUID.randomUUID()
        decisionRevision = 2
        recordedByPrincipalKind = PrincipalKind.USER
        recordedByPrincipalId = principal.id
    }

    private val factResource = InformationRequestAcceptedFactResource(facts, factQueries, decisions, accessContextFactory)
    private val offerResource = InformationRequestAcceptedFactOfferResource(facts, factQueries, decisions, accessContextFactory)
    private val noAuthOfferResource = InformationRequestNoAuthAcceptedFactOfferResource(facts, factQueries, decisions, readAccessService)
    private val decisionResource = InformationRequestBusinessDecisionResource(facts, factQueries, decisions, accessContextFactory)

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(readAccessService.resolve(eq(requestId), eq("link-token"), anyOrNull())).thenReturn(noAuthAccess)
    }

    @Test
    fun `facts, offers, and business decisions are subordinate to the request and offers reach both surfaces`()
    {
        assertEquals("/information-requests/{id}/accepted-facts", pathOf(InformationRequestAcceptedFactResourceOperations::class.java))
        assertEquals("/information-requests/{id}/accepted-fact-offers", pathOf(InformationRequestAcceptedFactOfferResourceOperations::class.java))
        assertEquals("no-auth/information-requests/{id}/accepted-fact-offers", pathOf(InformationRequestNoAuthAcceptedFactOfferResourceOperations::class.java))
        assertEquals("/information-requests/{id}/business-decisions", pathOf(InformationRequestBusinessDecisionResourceOperations::class.java))
    }

    @Test
    fun `a promotion delegates its source item, purpose, visibility, period, supersession, and key and answers the fact`()
    {
        whenever(facts.promote(any())).thenReturn(view)
        val packageId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val supersededId = UUID.randomUUID()
        val validTo = Timestamp.from(Instant.now().plusSeconds(86_400))

        val response = factResource.promote(
            requestId.toString(),
            PromoteInformationRequestAcceptedFactRequest(
                packageId = packageId,
                submissionItemId = itemId,
                purposeKey = "process.eligibility",
                policyBasisKey = "policy.reuse",
                visibility = InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES,
                validTo = validTo,
                supersedesFactId = supersededId,
            ),
            " promote-key ",
        )

        assertEquals(201, response.status)
        val dto = response.entity as InformationRequestAcceptedFactDto
        assertEquals(JsonPrimitive("Synthetic value"), dto.value)
        assertEquals(InformationRequestAcceptedFactConfidence.REVIEWED, dto.confidence)
        assertFalse(dto.revoked)
        val captured = argumentCaptor<PromoteInformationRequestAcceptedFactCommand>()
        verify(facts).promote(captured.capture())
        assertEquals(packageId, captured.firstValue.packageId)
        assertEquals(itemId, captured.firstValue.submissionItemId)
        assertEquals("policy.reuse", captured.firstValue.policyBasisKey)
        assertEquals(InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES, captured.firstValue.visibility)
        assertEquals(validTo.toInstant(), captured.firstValue.validTo)
        assertEquals(supersededId, captured.firstValue.supersedesFactId)
        assertEquals("promote-key", captured.firstValue.idempotencyKey)
        assertEquals(access, captured.firstValue.access)
    }

    @Test
    fun `a revocation needs a key and a revoked fact is refused with a stable reason`()
    {
        val factId = UUID.randomUUID()
        val keyless = factResource.revoke(requestId.toString(), factId.toString(), RevokeInformationRequestAcceptedFactRequest("reason.withdrawn"), null)
        assertEquals(400, keyless.status)
        verify(facts, never()).revoke(any())

        whenever(facts.revoke(any()))
            .thenReturn(
                view.copy(
                    revocation = InformationRequestAcceptedFactRevocation().apply {
                        this.factId = fact.id
                        reasonCode = "reason.withdrawn"
                        revokedByPrincipalKind = PrincipalKind.USER
                        revokedByPrincipalId = principal.id
                    },
                ),
            )
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.ACCEPTED_FACT_REVOKED, "This fact is revoked"))

        val revoked = factResource.revoke(requestId.toString(), factId.toString(), RevokeInformationRequestAcceptedFactRequest("reason.withdrawn"), "revoke-key")
        assertEquals(200, revoked.status)
        val dto = revoked.entity as InformationRequestAcceptedFactDto
        assertTrue(dto.revoked)
        assertEquals("reason.withdrawn", dto.revocationReasonCode)
        val captured = argumentCaptor<RevokeInformationRequestAcceptedFactCommand>()
        verify(facts).revoke(captured.capture())
        assertEquals(factId, captured.firstValue.factId)

        val again = factResource.revoke(requestId.toString(), factId.toString(), RevokeInformationRequestAcceptedFactRequest("reason.withdrawn"), "revoke-key-2")
        assertEquals(409, again.status)
        assertEquals(InformationRequestErrorCatalog.ACCEPTED_FACT_REVOKED, (again.entity as ResponseError).reasonCode)
    }

    @Test
    fun `offers are read for the caller on both surfaces and a link for another request is not found`()
    {
        val offer = InformationRequestAcceptedFactOffer(UUID.randomUUID(), "requirement.one", view, reconfirmationRequired = true)
        val elsewhereId = UUID.randomUUID()
        whenever(factQueries.offers(requestId, access)).thenReturn(listOf(offer))
        whenever(factQueries.offers(requestId, noAuthAccess)).thenReturn(listOf(offer))
        whenever(readAccessService.resolve(eq(elsewhereId), eq("link-token"), anyOrNull())).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            ),
        )

        val authenticated = offerResource.offers(requestId.toString())
        val linked = noAuthOfferResource.offers(requestId.toString(), "link-token", "session-token")
        val elsewhere = noAuthOfferResource.offers(elsewhereId.toString(), "link-token", "session-token")

        assertEquals(200, authenticated.status)
        val authenticatedOffer = (authenticated.entity as Array<*>).filterIsInstance<InformationRequestAcceptedFactOfferDto>().single()
        assertTrue(authenticatedOffer.reconfirmationRequired)
        assertEquals(fact.id, authenticatedOffer.fact.id)
        assertEquals(JsonPrimitive("Synthetic value"), authenticatedOffer.fact.value)
        val authenticatedJson = Json.encodeToString(InformationRequestAcceptedFactOfferDto.serializer(), authenticatedOffer)
        assertFalse(authenticatedJson.contains("subjectIdentityRefId"))
        assertFalse(authenticatedJson.contains("fieldDefinitionId"))
        assertFalse(authenticatedJson.contains("sourceInformationRequestId"))
        assertFalse(authenticatedJson.contains("sourcePackageId"))
        assertFalse(authenticatedJson.contains("sourceSubmissionItemId"))
        assertFalse(authenticatedJson.contains("sourceRequirementId"))
        assertFalse(authenticatedJson.contains("sourceReviewId"))
        assertFalse(authenticatedJson.contains("evidenceVersionIds"))
        assertEquals(200, linked.status)
        val linkedOffer = (linked.entity as Array<*>).filterIsInstance<InformationRequestAcceptedFactOfferDto>().single()
        val linkedJson = Json.encodeToString(InformationRequestAcceptedFactOfferDto.serializer(), linkedOffer)
        assertEquals(authenticatedJson, linkedJson)
        assertEquals(404, elsewhere.status)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, (elsewhere.entity as ResponseError).reasonCode)
        verify(factQueries).offers(requestId, noAuthAccess)
        verify(factQueries, never()).offers(eq(elsewhereId), any())
    }

    @Test
    fun `a business decision delegates its chain and answers the decision with the request revision`()
    {
        whenever(decisions.record(any())).thenReturn(InformationRequestBusinessDecisionResult(decision, "\"request:7\""))
        val decidedAt = Timestamp.from(Instant.now().minusSeconds(60))

        val response = decisionResource.record(
            requestId.toString(),
            RecordInformationRequestBusinessDecisionRequest(
                owningProcessKey = "process.intake",
                outcomeCode = "outcome.accepted",
                kind = InformationRequestBusinessDecisionKind.RECONSIDERATION,
                priorDecisionId = decision.priorDecisionId,
                decidedAt = decidedAt,
            ),
            "decision-key",
        )

        assertEquals(201, response.status)
        assertEquals("\"request:7\"", response.getHeaderString("ETag"))
        val dto = response.entity as InformationRequestBusinessDecisionDto
        assertEquals(2, dto.decisionRevision)
        assertTrue(dto.recordedByCaller)
        val captured = argumentCaptor<RecordInformationRequestBusinessDecisionCommand>()
        verify(decisions).record(captured.capture())
        assertEquals(InformationRequestBusinessDecisionKind.RECONSIDERATION, captured.firstValue.kind)
        assertEquals(decision.priorDecisionId, captured.firstValue.priorDecisionId)
        assertEquals(decidedAt.toInstant(), captured.firstValue.decidedAt)
        assertEquals("decision-key", captured.firstValue.idempotencyKey)
    }

    @Test
    fun `an invalid prior decision is refused and the decision list names who recorded each entry`()
    {
        whenever(decisions.record(any())).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.BUSINESS_DECISION_PRIOR_INVALID, "The prior decision is not the latest"),
        )
        val other = InformationRequestBusinessDecision().apply {
            informationRequestId = requestId
            owningProcessKey = "process.intake"
            outcomeCode = "outcome.accepted"
            recordedByPrincipalKind = PrincipalKind.USER
            recordedByPrincipalId = UUID.randomUUID()
        }
        whenever(decisions.decisions(requestId, access)).thenReturn(listOf(other))

        val refused = decisionResource.record(
            requestId.toString(),
            RecordInformationRequestBusinessDecisionRequest("process.intake", "outcome.accepted", decidedAt = Timestamp.from(Instant.now())),
            "decision-key",
        )
        val listed = decisionResource.list(requestId.toString())

        assertEquals(409, refused.status)
        assertEquals(InformationRequestErrorCatalog.BUSINESS_DECISION_PRIOR_INVALID, (refused.entity as ResponseError).reasonCode)
        assertEquals(200, listed.status)
        assertFalse((listed.entity as Array<*>).filterIsInstance<InformationRequestBusinessDecisionDto>().single().recordedByCaller)
    }

    private fun pathOf(resourceClass: Class<*>): String = resourceClass.getAnnotation(Path::class.java).value
}
