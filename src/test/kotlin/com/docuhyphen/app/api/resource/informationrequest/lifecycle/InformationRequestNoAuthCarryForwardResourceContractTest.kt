package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.model.dto.InformationRequestCarryForwardDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestCarryForwardOffer
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestNoAuthCarryForwardResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthSessionFixture
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCarryForwardPlanner
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestNoAuthCarryForwardResourceContractTest {
    private val requestId = UUID.randomUUID()
    private val access = RequestAccessContext(
        PrincipalRef.participant(UUID.randomUUID()),
        AuthorizationContext(sessionRef = "link-session")
    )
    private val lineage: InformationRequestLineageQueryService = mock()
    private val readAccess: InformationRequestNoAuthReadAccessService = InformationRequestNoAuthAccessDoubles.current()
    private val resource = InformationRequestNoAuthCarryForwardResource(lineage, readAccess)

    @Test
    fun `carry-forward offers are a read-only no-auth sub-collection routed only by the operations interface`() {
        val operations = InformationRequestNoAuthCarryForwardResourceOperations::class.java
        val list = operations.declaredMethods.single()

        assertEquals(
            "no-auth/information-requests/{id}/carry-forwards",
            operations.getAnnotation(Path::class.java).value
        )
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Produces::class.java).value)
        assertEquals("list", list.name)
        assertTrue(list.isAnnotationPresent(GET::class.java))
        assertEquals("id", list.parameters.first().getAnnotation(PathParam::class.java).value)
        assertEquals(
            listOf(
                InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER,
                InformationRequestCommandHttp.SESSION_TOKEN_HEADER
            ),
            list.parameters.drop(1).map { it.getAnnotation(HeaderParam::class.java)?.value },
        )
        val resourceClass = InformationRequestNoAuthCarryForwardResource::class.java
        assertTrue(resourceClass.annotations.none(::isJaxRs))
        assertTrue(resourceClass.declaredMethods.none { method ->
            method.annotations.any(::isJaxRs) || method.parameterAnnotations.flatten().any(::isJaxRs)
        })
    }

    @Test
    fun `each carry-forward answers its decision and only the prior answer it may still reuse`() {
        val sourcePackageId = UUID.randomUUID()
        val offered = carryForward(sourcePackageId, InformationRequestCarryForwardDecision.OFFERED)
        val invalidated = carryForward(
            sourcePackageId,
            InformationRequestCarryForwardDecision.INVALIDATED,
            InformationRequestCarryForwardPlanner.EVIDENCE_REQUIRES_FRESH_COLLECTION,
        )
        val cleared = carryForward(sourcePackageId, InformationRequestCarryForwardDecision.OFFERED)
        whenever(readAccess.resolve(requestId, LINK_TOKEN, SESSION_TOKEN)).thenReturn(access)
        whenever(lineage.carryForwards(requestId, access)).thenReturn(
            listOf(
                InformationRequestCarryForwardOffer(
                    offered,
                    sourceItem("Prior narrative"),
                    priorValue("Prior value", cleared = false)
                ),
                InformationRequestCarryForwardOffer(invalidated, null, null),
                InformationRequestCarryForwardOffer(
                    cleared,
                    sourceItem(null),
                    priorValue("Withdrawn value", cleared = true)
                ),
            ),
        )

        val response = resource.list(requestId.toString(), LINK_TOKEN, SESSION_TOKEN)

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertNull(response.getHeaderString(HttpHeaders.ETAG))
        assertEquals(
            listOf(
                InformationRequestCarryForwardDto(
                    requirementId = offered.informationRequestRequirementId,
                    decision = InformationRequestCarryForwardDecision.OFFERED,
                    sourcePackageId = sourcePackageId,
                    priorDisposition = InformationRequestResponseDisposition.PROVIDED,
                    priorNarrative = "Prior narrative",
                    priorFieldValue = JsonPrimitive("Prior value"),
                ),
                InformationRequestCarryForwardDto(
                    requirementId = invalidated.informationRequestRequirementId,
                    decision = InformationRequestCarryForwardDecision.INVALIDATED,
                    reasonCode = InformationRequestCarryForwardPlanner.EVIDENCE_REQUIRES_FRESH_COLLECTION,
                    sourcePackageId = sourcePackageId,
                ),
                InformationRequestCarryForwardDto(
                    requirementId = cleared.informationRequestRequirementId,
                    decision = InformationRequestCarryForwardDecision.OFFERED,
                    sourcePackageId = sourcePackageId,
                    priorDisposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
            (response.entity as Array<*>).toList(),
        )
        verify(lineage).carryForwards(requestId, access)
    }

    @Test
    fun `a malformed request id or a missing access link token is refused before any credential or lineage read`() {
        val refusals = listOf(
            resource.list("not-a-uuid", LINK_TOKEN, SESSION_TOKEN),
            resource.list(requestId.toString(), null, SESSION_TOKEN),
            resource.list(requestId.toString(), "  ", SESSION_TOKEN),
        )

        assertEquals(List(3) { Response.Status.BAD_REQUEST.statusCode }, refusals.map { it.status })
        assertEquals(
            listOf(
                "Invalid information request id",
                "An access link token is required",
                "An access link token is required"
            ),
            refusals.map { (it.entity as ResponseError).errorMessage },
        )
        verifyNoInteractions(readAccess, lineage)
    }

    @Test
    fun `credential and lineage refusals keep their status and reason`() {
        whenever(readAccess.resolve(requestId, "unverified-link", null)).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
                "A verified request session credential is required",
            ),
        )
        whenever(readAccess.resolve(requestId, "foreign-link", SESSION_TOKEN))
            .thenThrow(ForbiddenException("Session is not bound to this ShareLink"))
        whenever(readAccess.resolve(requestId, LINK_TOKEN, SESSION_TOKEN)).thenReturn(access)
        whenever(lineage.carryForwards(requestId, access))
            .thenThrow(ForbiddenException("Access denied"))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request not found"
                )
            )
            .thenThrow(RuntimeException("Lineage store unavailable"))

        val unverified = resource.list(requestId.toString(), "unverified-link", null)
        val foreign = resource.list(requestId.toString(), "foreign-link", SESSION_TOKEN)
        val forbidden = resource.list(requestId.toString(), LINK_TOKEN, SESSION_TOKEN)
        val missing = resource.list(requestId.toString(), LINK_TOKEN, SESSION_TOKEN)
        val failed = resource.list(requestId.toString(), LINK_TOKEN, SESSION_TOKEN)

        assertRefused(Response.Status.CONFLICT, InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED, unverified)
        assertRefused(Response.Status.FORBIDDEN, InformationRequestErrorCatalog.FORBIDDEN, foreign)
        assertRefused(Response.Status.FORBIDDEN, InformationRequestErrorCatalog.FORBIDDEN, forbidden)
        assertRefused(Response.Status.NOT_FOUND, InformationRequestErrorCatalog.NOT_FOUND, missing)
        assertRefused(Response.Status.INTERNAL_SERVER_ERROR, null, failed)
        assertEquals("Request failed", (failed.entity as ResponseError).errorMessage)
    }

    @Test
    fun `a session verified for another request is answered not found before any lineage read or session use`() {
        val fixture = InformationRequestNoAuthSessionFixture()
        val issued = fixture.issue()
        val bound = InformationRequestNoAuthCarryForwardResource(lineage, fixture.service)

        val response = bound.list(
            requestId.toString(),
            InformationRequestNoAuthSessionFixture.BOOTSTRAP_TOKEN,
            issued.sessionToken
        )

        assertRefused(Response.Status.NOT_FOUND, InformationRequestErrorCatalog.NOT_FOUND, response)
        verifyNoInteractions(lineage)
        assertEquals(0, issued.session.useCount)
        assertNull(issued.session.lastUsedAt)
    }

    private fun assertRefused(status: Response.Status, reasonCode: String?, response: Response) {
        assertEquals(status.statusCode, response.status)
        assertEquals(reasonCode, (response.entity as ResponseError).reasonCode)
    }

    private fun isJaxRs(annotation: Annotation): Boolean =
        annotation.annotationClass.java.name.let { it.startsWith("jakarta.ws.rs.") || it.startsWith("org.jboss.resteasy.reactive.") }

    private fun carryForward(
        sourcePackageId: UUID,
        decision: InformationRequestCarryForwardDecision,
        reasonCode: String? = null,
    ) = InformationRequestCarryForward().apply {
        lineageId = UUID.randomUUID()
        informationRequestId = requestId
        informationRequestRequirementId = UUID.randomUUID()
        this.sourcePackageId = sourcePackageId
        sourceItemId = UUID.randomUUID()
        this.decision = decision
        this.reasonCode = reasonCode
    }

    private fun sourceItem(narrative: String?) = InformationRequestSubmissionItem().apply {
        disposition = InformationRequestResponseDisposition.PROVIDED
        this.narrative = narrative
    }

    private fun priorValue(value: String, cleared: Boolean) =
        FieldValueRevisionValue(
            UUID.randomUUID(),
            UUID.randomUUID(),
            FieldValueType.SHORT_TEXT,
            cleared,
            JsonPrimitive(value)
        )

    private companion object {
        const val LINK_TOKEN = "link-token"
        const val SESSION_TOKEN = "session-token"
    }
}
