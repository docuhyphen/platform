package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.InformationRequestCarryForwardDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestCarryForwardOffer
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.handler.InformationRequestCarryForwardRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestCarryForwardResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.lang.reflect.Method
import java.util.*

class InformationRequestCarryForwardResourceContractTest {
    private val requestId = UUID.randomUUID()
    private val packageId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "respondent"))
    private val lineageQueries = mock<InformationRequestLineageQueryService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory> {
        on { currentAuthenticated() } doReturn access
    }
    private val resource = InformationRequestCarryForwardResource(lineageQueries, accessContextFactory)

    @Test
    fun `carry-forward offers are a read-only sub-collection declared only on the operations interface`() {
        val operations = InformationRequestCarryForwardResourceOperations::class.java
        val list = operations.declaredMethods.single()

        assertEquals("/information-requests/{id}/carry-forwards", operations.getAnnotation(Path::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Produces::class.java).value)
        assertEquals("list", list.name)
        assertTrue(list.isAnnotationPresent(GET::class.java))
        assertNull(list.getAnnotation(Path::class.java))
        assertEquals(listOf("path:id"), parametersOf(list))
        assertTrue(operations.isAssignableFrom(InformationRequestCarryForwardResource::class.java))
        assertEquals(emptyList<String>(), jaxRsAnnotationsOn(InformationRequestCarryForwardResource::class.java))
        assertEquals(emptyList<String>(), jaxRsAnnotationsOn(InformationRequestCarryForwardRequestHandler::class.java))
    }

    @Test
    fun `each offer carries the prior answer only while it is offered and its field value is not cleared`() {
        val kept = offer(InformationRequestCarryForwardDecision.OFFERED, null)
        val cleared = offer(InformationRequestCarryForwardDecision.OFFERED, null)
        val invalidated = offer(InformationRequestCarryForwardDecision.INVALIDATED, "requirement_changed")
        val priorItem = InformationRequestSubmissionItem().apply {
            disposition = InformationRequestResponseDisposition.PROVIDED
            narrative = "The earlier answer still applies"
        }
        whenever(lineageQueries.carryForwards(requestId, access)).thenReturn(
            listOf(
                InformationRequestCarryForwardOffer(kept, priorItem, fieldValue(cleared = false)),
                InformationRequestCarryForwardOffer(cleared, priorItem, fieldValue(cleared = true)),
                InformationRequestCarryForwardOffer(invalidated, null, null),
            ),
        )

        val response = resource.list(requestId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val offers = (response.entity as Array<InformationRequestCarryForwardDto>).toList()
        assertEquals(
            listOf(
                InformationRequestCarryForwardDto(
                    requirementId = kept.informationRequestRequirementId,
                    decision = InformationRequestCarryForwardDecision.OFFERED,
                    sourcePackageId = packageId,
                    priorDisposition = InformationRequestResponseDisposition.PROVIDED,
                    priorNarrative = "The earlier answer still applies",
                    priorFieldValue = JsonPrimitive("Reference 7"),
                ),
                InformationRequestCarryForwardDto(
                    requirementId = cleared.informationRequestRequirementId,
                    decision = InformationRequestCarryForwardDecision.OFFERED,
                    sourcePackageId = packageId,
                    priorDisposition = InformationRequestResponseDisposition.PROVIDED,
                    priorNarrative = "The earlier answer still applies",
                ),
                InformationRequestCarryForwardDto(
                    requirementId = invalidated.informationRequestRequirementId,
                    decision = InformationRequestCarryForwardDecision.INVALIDATED,
                    reasonCode = "requirement_changed",
                    sourcePackageId = packageId,
                ),
            ),
            offers,
        )
    }

    @Test
    fun `a malformed request id is refused before the lineage query runs`() {
        val response = resource.list("not-a-uuid")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        assertEquals("Invalid information request id", (response.entity as ResponseError).errorMessage)
        verifyNoInteractions(lineageQueries)
    }

    @Test
    fun `refusals keep their status and reason code`() {
        assertRefused(
            ForbiddenException("Access denied"),
            Response.Status.FORBIDDEN.statusCode,
            InformationRequestErrorCatalog.FORBIDDEN
        )
        assertRefused(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            ),
            Response.Status.NOT_FOUND.statusCode,
            InformationRequestErrorCatalog.NOT_FOUND,
        )
        assertRefused(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
                "The parent Exchange is not readable"
            ),
            Response.Status.CONFLICT.statusCode,
            InformationRequestErrorCatalog.PARENT_STATE_INVALID,
        )
        assertRefused(RuntimeException("connection reset"), Response.Status.INTERNAL_SERVER_ERROR.statusCode, null)
        assertEquals("Request failed", (resource.list(requestId.toString()).entity as ResponseError).errorMessage)
    }

    @Test
    fun `a plan refusal is rethrown for the subscription mapper`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED,
                planCode = PlanCode.BUSINESS,
                ownerType = SubscriptionOwnerType.ORGANIZATION,
                message = "The owner's subscription is suspended",
            ),
        )
        doThrow(denial).whenever(lineageQueries).carryForwards(any(), any())

        assertSame(denial, assertThrows<SubscriptionDenialException> { resource.list(requestId.toString()) })
    }

    private fun assertRefused(exception: RuntimeException, status: Int, reasonCode: String?) {
        doThrow(exception).whenever(lineageQueries).carryForwards(any(), any())

        val response = resource.list(requestId.toString())

        assertEquals(status, response.status, exception.javaClass.simpleName)
        assertEquals(reasonCode, (response.entity as ResponseError).reasonCode)
    }

    private fun offer(decision: InformationRequestCarryForwardDecision, reasonCode: String?) =
        InformationRequestCarryForward().apply {
            lineageId = UUID.randomUUID()
            informationRequestId = requestId
            informationRequestRequirementId = UUID.randomUUID()
            sourcePackageId = packageId
            sourceItemId = UUID.randomUUID()
            this.decision = decision
            this.reasonCode = reasonCode
        }

    private fun fieldValue(cleared: Boolean) = FieldValueRevisionValue(
        revisionId = UUID.randomUUID(),
        fieldContractId = UUID.randomUUID(),
        valueType = FieldValueType.SHORT_TEXT,
        cleared = cleared,
        value = JsonPrimitive("Reference 7"),
    )

    private fun parametersOf(method: Method): List<String> =
        method.parameters.map { parameter ->
            parameter.getAnnotation(PathParam::class.java)?.let { "path:${it.value}" }
                ?: parameter.getAnnotation(HeaderParam::class.java)?.let { "header:${it.value}" }
                ?: "body"
        }

    private fun jaxRsAnnotationsOn(type: Class<*>): List<String> =
        (type.annotations.toList() + type.declaredMethods.flatMap { method ->
            method.annotations.toList() + method.parameterAnnotations.flatMap { it.toList() }
        })
            .map { it.annotationClass.java.name }
            .filter { it.startsWith("jakarta.ws.rs.") }
}
