package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestLineageDto
import com.docuhyphen.app.api.model.dto.InformationRequestRefreshRuleDto
import com.docuhyphen.app.api.model.dto.InformationRequestSuccessorResultDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestRefreshRuleResourceOperations
import com.docuhyphen.app.api.resource.model.DefineInformationRequestRefreshRuleRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestHeaderProjectionService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.execution.ExecutionStandingTestDoubles
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestFollowUpService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.lang.reflect.Method
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestRefreshRuleResourceContractTest {
    private val requestId = UUID.randomUUID()
    private val ruleId = UUID.randomUUID()
    private val packageId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "coordinator"))
    private val source = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        state = InformationRequestState.CLOSED
        aggregateRevision = 9
    }
    private val readETag = InformationRequestETag.aggregateOf(source)
    private val staleETag = "\"aggregate:$requestId:8\""
    private val definedETag = "\"aggregate:$requestId:10\""
    private val successor = InformationRequest().apply {
        exchangeId = source.exchangeId
        templateVersionId = source.templateVersionId
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = source.ownerOrganizationId
    }
    private val successorETag = InformationRequestETag.aggregateOf(successor)
    private val rule = InformationRequestRefreshRule().apply {
        id = ruleId
        informationRequestId = requestId
        requirementKey = "periodic-document"
        leadDays = 30
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = access.principal.id
        createdAt = Timestamp.from(Instant.parse("2026-10-06T08:00:00Z"))
    }
    private val lineage = InformationRequestLineage().apply {
        successorRequestId = successor.id
        sourceRequestId = requestId
        sourcePackageId = packageId
        lineageKind = InformationRequestLineageKind.REFRESH
        refreshRuleId = ruleId
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = access.principal.id
        createdAt = Timestamp.from(Instant.parse("2027-03-01T09:00:00Z"))
    }
    private val refreshed = InformationRequestSuccessorResult(
        source = source,
        successor = successor,
        lineage = lineage,
        carryForwards = listOf(
            carryForward(InformationRequestCarryForwardDecision.OFFERED),
            carryForward(InformationRequestCarryForwardDecision.INVALIDATED),
            carryForward(InformationRequestCarryForwardDecision.INVALIDATED),
        ),
        successorETag = successorETag,
    )
    private val followUps = mock<InformationRequestFollowUpService>()
    private val authorizationService = mock<AuthorizationService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory> {
        on { currentAuthenticated() } doReturn access
    }
    private val resource = InformationRequestRefreshRuleResource(
        followUps,
        accessContextFactory,
        InformationRequestHeaderProjectionService(authorizationService, ExecutionStandingTestDoubles.active()),
    )

    init {
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
    }

    @Test
    fun `refresh rule routes are declared only on the operations interface`() {
        val operations = InformationRequestRefreshRuleResourceOperations::class.java
        val methods = operations.declaredMethods.associateBy { it.name }

        assertEquals("/information-requests/{id}/refresh-rules", operations.getAnnotation(Path::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Produces::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Consumes::class.java).value)
        assertEquals(setOf("define", "refresh"), methods.keys)
        assertTrue(methods.getValue("define").isAnnotationPresent(POST::class.java))
        assertNull(methods.getValue("define").getAnnotation(Path::class.java))
        assertEquals(
            listOf("path:id", "body", "header:${HttpHeaders.IF_MATCH}", "header:$IDEMPOTENCY_KEY"),
            parametersOf(methods.getValue("define"))
        )
        assertTrue(methods.getValue("refresh").isAnnotationPresent(POST::class.java))
        assertEquals("/{ruleId}/refreshes", methods.getValue("refresh").getAnnotation(Path::class.java).value)
        assertEquals(
            listOf("path:id", "path:ruleId", "header:$IDEMPOTENCY_KEY"),
            parametersOf(methods.getValue("refresh"))
        )
        assertTrue(operations.isAssignableFrom(InformationRequestRefreshRuleResource::class.java))
        assertEquals(emptyList<String>(), jaxRsAnnotationsOn(InformationRequestRefreshRuleResource::class.java))
    }

    @Test
    fun `defining a refresh rule passes the requirement, lead days, precondition, and key and answers 201 with the rule and the request ETag it produced`() {
        whenever(followUps.defineRefreshRule(any())).thenReturn(
            InformationRequestRefreshRuleDefinition(
                rule,
                definedETag
            )
        )

        val response = resource.define(requestId.toString(), ruleRequest(), readETag, " rule-key ")

        verify(followUps).defineRefreshRule(
            DefineInformationRequestRefreshRuleCommand(
                requestId = requestId,
                requirementKey = "periodic-document",
                leadDays = 30,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(readETag),
                idempotencyKey = "rule-key",
            ),
        )
        assertEquals(Response.Status.CREATED.statusCode, response.status)
        assertEquals(definedETag, response.getHeaderString(HttpHeaders.ETAG))
        assertEquals(
            InformationRequestRefreshRuleDto(
                id = ruleId,
                informationRequestId = requestId,
                requirementKey = "periodic-document",
                leadDays = 30,
                createdAt = rule.createdAt,
            ),
            response.entity,
        )
    }

    @Test
    fun `refreshing answers 201 with the successor header, lineage, carry-forward counts, and successor ETag`() {
        whenever(followUps.refresh(any())).thenReturn(refreshed)

        val response = resource.refresh(requestId.toString(), ruleId.toString(), " refresh-key ")

        verify(followUps).refresh(RefreshInformationRequestCommand(requestId, ruleId, access, "refresh-key"))
        assertEquals(Response.Status.CREATED.statusCode, response.status)
        assertEquals(successorETag, response.getHeaderString(HttpHeaders.ETAG))
        assertEquals(
            InformationRequestSuccessorResultDto(
                sourceRequestId = requestId,
                sourceState = InformationRequestState.CLOSED,
                successor = InformationRequestDtoMapper.toDto(
                    successor,
                    true,
                    InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE),
                ),
                successorETag = successorETag,
                lineage = InformationRequestLineageDto(
                    id = lineage.id,
                    lineageKind = InformationRequestLineageKind.REFRESH,
                    sourceRequestId = requestId,
                    successorRequestId = successor.id,
                    sourcePackageId = packageId,
                    refreshRuleId = ruleId,
                    createdAt = lineage.createdAt,
                ),
                offeredCount = 1,
                invalidatedCount = 2,
            ),
            response.entity,
        )
    }

    @Test
    fun `malformed ids and missing keys are refused before the follow-up service runs`() {
        val refusals = mapOf(
            "Invalid information request id" to listOf(
                resource.define("not-a-uuid", ruleRequest(), readETag, "rule-key"),
                resource.refresh("not-a-uuid", ruleId.toString(), "refresh-key"),
            ),
            "Invalid refresh rule id" to listOf(
                resource.refresh(requestId.toString(), "not-a-uuid", "refresh-key"),
            ),
            "Idempotency-Key is required" to listOf(
                resource.define(requestId.toString(), ruleRequest(), readETag, ""),
                resource.refresh(requestId.toString(), ruleId.toString(), null),
            ),
        )

        refusals.forEach { (message, responses) ->
            responses.forEach { response ->
                assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status, message)
                assertEquals(message, (response.entity as ResponseError).errorMessage)
            }
        }
        verifyNoInteractions(followUps, authorizationService)
    }

    @Test
    fun `a refresh rule definition without a body is refused before the follow-up service runs`() {
        val response = resource.define(requestId.toString(), null, readETag, "rule-key")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        assertEquals(
            "A refresh rule names its requirement key and lead days",
            (response.entity as ResponseError).errorMessage
        )
        verifyNoInteractions(followUps)
    }

    @Test
    fun `a refresh rule definition without If-Match is answered 428 and a stale one 412, both with the current request ETag`() {
        whenever(followUps.defineRefreshRule(any())).thenAnswer { invocation ->
            invocation.getArgument<DefineInformationRequestRefreshRuleCommand>(0).precondition.requireSatisfiedBy(
                readETag
            )
            InformationRequestRefreshRuleDefinition(rule, definedETag)
        }

        val missing = resource.define(requestId.toString(), ruleRequest(), null, "rule-key")
        val stale = resource.define(requestId.toString(), ruleRequest(), staleETag, "rule-key")
        val current = resource.define(requestId.toString(), ruleRequest(), readETag, "rule-key")

        assertEquals(428, missing.status)
        assertEquals(
            CommandPreconditionException.Kind.REQUIRED.reasonCode,
            (missing.entity as ResponseError).reasonCode
        )
        assertEquals(readETag, missing.getHeaderString(HttpHeaders.ETAG))
        assertEquals(Response.Status.PRECONDITION_FAILED.statusCode, stale.status)
        assertEquals(CommandPreconditionException.Kind.STALE.reasonCode, (stale.entity as ResponseError).reasonCode)
        assertEquals(readETag, stale.getHeaderString(HttpHeaders.ETAG))
        assertEquals(Response.Status.CREATED.statusCode, current.status)
        assertEquals(definedETag, current.getHeaderString(HttpHeaders.ETAG))
    }

    @Test
    fun `refusals keep their status and reason code on every refresh rule route`() {
        assertRefusedEverywhere(
            ForbiddenException("Access denied"),
            Response.Status.FORBIDDEN.statusCode,
            InformationRequestErrorCatalog.FORBIDDEN
        )
        assertRefusedEverywhere(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Refresh rule not found"),
            Response.Status.NOT_FOUND.statusCode,
            InformationRequestErrorCatalog.NOT_FOUND,
        )
        assertRefusedEverywhere(
            CommandReceiptConflictException(),
            Response.Status.CONFLICT.statusCode,
            "COMMAND_RECEIPT_FINGERPRINT_CONFLICT"
        )
        assertRefusedEverywhere(
            RuntimeException("connection reset"),
            Response.Status.INTERNAL_SERVER_ERROR.statusCode,
            null
        )
        routes().forEach { (name, call) ->
            assertEquals(
                "Request failed",
                (call().entity as ResponseError).errorMessage,
                name
            )
        }

        doThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REFRESH_EXPIRY_NOT_CAPTURED,
                "A refresh rule names a requested document whose expiry date is captured",
            ),
        ).whenever(followUps).defineRefreshRule(any())
        doThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REFRESH_NOT_DUE,
                "The evidence this refresh rule renews is not yet within its lead time",
            ),
        ).whenever(followUps).refresh(any())
        val uncaptured = routes().getValue("define")()
        val notDue = routes().getValue("refresh")()

        assertEquals(Response.Status.CONFLICT.statusCode, uncaptured.status)
        assertEquals(
            InformationRequestErrorCatalog.REFRESH_EXPIRY_NOT_CAPTURED,
            (uncaptured.entity as ResponseError).reasonCode
        )
        assertEquals(Response.Status.CONFLICT.statusCode, notDue.status)
        assertEquals(InformationRequestErrorCatalog.REFRESH_NOT_DUE, (notDue.entity as ResponseError).reasonCode)
    }

    @Test
    fun `a plan refusal on any refresh rule route is rethrown for the subscription mapper`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.SUBSCRIPTION_CANCELED,
                planCode = PlanCode.BUSINESS,
                ownerType = SubscriptionOwnerType.ORGANIZATION,
                message = "The owner's subscription is canceled",
            ),
        )
        failEveryCallWith(denial)

        routes().forEach { (name, call) ->
            assertSame(denial, assertThrows<SubscriptionDenialException>(name) { call() })
        }
    }

    private fun assertRefusedEverywhere(exception: RuntimeException, status: Int, reasonCode: String?) {
        failEveryCallWith(exception)
        routes().forEach { (name, call) ->
            val response = call()
            assertEquals(status, response.status, "$name refused with ${exception.javaClass.simpleName}")
            assertEquals(reasonCode, (response.entity as ResponseError).reasonCode, name)
        }
    }

    private fun failEveryCallWith(exception: RuntimeException) {
        doThrow(exception).whenever(followUps).defineRefreshRule(any())
        doThrow(exception).whenever(followUps).refresh(any())
    }

    private fun routes(): Map<String, () -> Response> = mapOf(
        "define" to { resource.define(requestId.toString(), ruleRequest(), readETag, "rule-key") },
        "refresh" to { resource.refresh(requestId.toString(), ruleId.toString(), "refresh-key") },
    )

    private fun ruleRequest() =
        DefineInformationRequestRefreshRuleRequest(requirementKey = "periodic-document", leadDays = 30)

    private fun carryForward(decision: InformationRequestCarryForwardDecision) =
        InformationRequestCarryForward().apply {
            lineageId = lineage.id
            informationRequestId = successor.id
            informationRequestRequirementId = UUID.randomUUID()
            sourcePackageId = packageId
            sourceItemId = UUID.randomUUID()
            this.decision = decision
        }

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

    private companion object {
        const val IDEMPOTENCY_KEY = InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
    }
}
