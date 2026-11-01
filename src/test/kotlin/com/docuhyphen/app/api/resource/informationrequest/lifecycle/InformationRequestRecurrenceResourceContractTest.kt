package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestLineageDto
import com.docuhyphen.app.api.model.dto.InformationRequestRecurrenceDto
import com.docuhyphen.app.api.model.dto.InformationRequestSuccessorResultDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestRecurrenceResourceOperations
import com.docuhyphen.app.api.resource.model.DefineInformationRequestRecurrenceRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
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
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
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

class InformationRequestRecurrenceResourceContractTest {
    private val requestId = UUID.randomUUID()
    private val recurrenceId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "coordinator"))
    private val firstDue = Instant.parse("2026-11-02T09:00:00Z")
    private val source = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        state = InformationRequestState.CLOSED
        aggregateRevision = 6
    }
    private val readETag = InformationRequestETag.aggregateOf(source)
    private val staleETag = "\"aggregate:$requestId:5\""
    private val definedETag = "\"aggregate:$requestId:7\""
    private val successor = InformationRequest().apply {
        exchangeId = source.exchangeId
        templateVersionId = source.templateVersionId
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = source.ownerOrganizationId
    }
    private val successorETag = InformationRequestETag.aggregateOf(successor)
    private val recurrence = InformationRequestRecurrence().apply {
        id = recurrenceId
        originRequestId = requestId
        intervalUnit = InformationRequestRecurrenceUnit.MONTH
        intervalCount = 3
        firstDueAt = Timestamp.from(firstDue)
        maximumOccurrences = 4
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = access.principal.id
        createdAt = Timestamp.from(Instant.parse("2026-10-06T08:00:00Z"))
    }
    private val lineage = InformationRequestLineage().apply {
        successorRequestId = successor.id
        sourceRequestId = requestId
        lineageKind = InformationRequestLineageKind.RECURRENCE
        recurrenceId = this@InformationRequestRecurrenceResourceContractTest.recurrenceId
        recurrenceSequence = 2
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = access.principal.id
        createdAt = Timestamp.from(Instant.parse("2027-02-02T09:00:00Z"))
    }
    private val occurrence = InformationRequestSuccessorResult(
        source = source,
        successor = successor,
        lineage = lineage,
        carryForwards = listOf(
            carryForward(InformationRequestCarryForwardDecision.OFFERED),
            carryForward(InformationRequestCarryForwardDecision.OFFERED),
            carryForward(InformationRequestCarryForwardDecision.INVALIDATED),
        ),
        successorETag = successorETag,
    )
    private val followUps = mock<InformationRequestFollowUpService>()
    private val authorizationService = mock<AuthorizationService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory> {
        on { currentAuthenticated() } doReturn access
    }
    private val resource = InformationRequestRecurrenceResource(
        followUps,
        accessContextFactory,
        InformationRequestHeaderProjectionService(authorizationService, ExecutionStandingTestDoubles.active()),
    )

    init {
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
    }

    @Test
    fun `recurrence routes are declared only on the operations interface`() {
        val operations = InformationRequestRecurrenceResourceOperations::class.java
        val methods = operations.declaredMethods.associateBy { it.name }

        assertEquals("/information-requests/{id}/recurrences", operations.getAnnotation(Path::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Produces::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Consumes::class.java).value)
        assertEquals(setOf("define", "createNextOccurrence"), methods.keys)
        assertTrue(methods.getValue("define").isAnnotationPresent(POST::class.java))
        assertNull(methods.getValue("define").getAnnotation(Path::class.java))
        assertEquals(
            listOf("path:id", "body", "header:${HttpHeaders.IF_MATCH}", "header:$IDEMPOTENCY_KEY"),
            parametersOf(methods.getValue("define"))
        )
        assertTrue(methods.getValue("createNextOccurrence").isAnnotationPresent(POST::class.java))
        assertEquals(
            "/{recurrenceId}/occurrences",
            methods.getValue("createNextOccurrence").getAnnotation(Path::class.java).value
        )
        assertEquals(
            listOf("path:id", "path:recurrenceId", "header:$IDEMPOTENCY_KEY"),
            parametersOf(methods.getValue("createNextOccurrence"))
        )
        assertTrue(operations.isAssignableFrom(InformationRequestRecurrenceResource::class.java))
        assertEquals(emptyList<String>(), jaxRsAnnotationsOn(InformationRequestRecurrenceResource::class.java))
    }

    @Test
    fun `defining a recurrence parses its schedule and answers 201 with the recurrence and the request ETag it produced`() {
        whenever(followUps.defineRecurrence(any())).thenReturn(
            InformationRequestRecurrenceDefinition(
                recurrence,
                definedETag
            )
        )

        val response = resource.define(requestId.toString(), schedule(), readETag, " recurrence-key ")

        verify(followUps).defineRecurrence(
            DefineInformationRequestRecurrenceCommand(
                requestId = requestId,
                intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                intervalCount = 3,
                firstDueAt = firstDue,
                maximumOccurrences = 4,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(readETag),
                idempotencyKey = "recurrence-key",
            ),
        )
        assertEquals(Response.Status.CREATED.statusCode, response.status)
        assertEquals(definedETag, response.getHeaderString(HttpHeaders.ETAG))
        assertEquals(
            InformationRequestRecurrenceDto(
                id = recurrenceId,
                originRequestId = requestId,
                intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                intervalCount = 3,
                firstDueAt = Timestamp.from(firstDue),
                maximumOccurrences = 4,
                createdAt = recurrence.createdAt,
            ),
            response.entity,
        )
    }

    @Test
    fun `creating the next occurrence answers 201 with the successor header, lineage, carry-forward counts, and successor ETag`() {
        whenever(followUps.createNextOccurrence(any())).thenReturn(occurrence)

        val response = resource.createNextOccurrence(requestId.toString(), recurrenceId.toString(), " occurrence-key ")

        verify(followUps).createNextOccurrence(
            CreateNextInformationRequestOccurrenceCommand(
                requestId,
                recurrenceId,
                access,
                "occurrence-key"
            )
        )
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
                    lineageKind = InformationRequestLineageKind.RECURRENCE,
                    sourceRequestId = requestId,
                    successorRequestId = successor.id,
                    recurrenceId = recurrenceId,
                    recurrenceSequence = 2,
                    createdAt = lineage.createdAt,
                ),
                offeredCount = 2,
                invalidatedCount = 1,
            ),
            response.entity,
        )
    }

    @Test
    fun `malformed ids, an unreadable first due time, and missing keys are refused before the follow-up service runs`() {
        val refusals = mapOf(
            "Invalid information request id" to listOf(
                resource.define("not-a-uuid", schedule(), readETag, "recurrence-key"),
                resource.createNextOccurrence("not-a-uuid", recurrenceId.toString(), "occurrence-key"),
            ),
            "Invalid recurrence id" to listOf(
                resource.createNextOccurrence(requestId.toString(), "not-a-uuid", "occurrence-key"),
            ),
            "The first due time is not an ISO instant" to listOf(
                resource.define(
                    requestId.toString(),
                    schedule().copy(firstDueAt = "next quarter"),
                    readETag,
                    "recurrence-key"
                ),
            ),
            "Idempotency-Key is required" to listOf(
                resource.define(requestId.toString(), schedule(), readETag, null),
                resource.createNextOccurrence(requestId.toString(), recurrenceId.toString(), " "),
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
    fun `a recurrence definition without a body is refused before the follow-up service runs`() {
        val response = resource.define(requestId.toString(), null, readETag, "recurrence-key")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        assertEquals(
            "A recurrence states its interval and first due time",
            (response.entity as ResponseError).errorMessage
        )
        verifyNoInteractions(followUps)
    }

    @Test
    fun `a recurrence definition without If-Match is answered 428 and a stale one 412, both with the current request ETag`() {
        whenever(followUps.defineRecurrence(any())).thenAnswer { invocation ->
            invocation.getArgument<DefineInformationRequestRecurrenceCommand>(0).precondition.requireSatisfiedBy(
                readETag
            )
            InformationRequestRecurrenceDefinition(recurrence, definedETag)
        }

        val missing = resource.define(requestId.toString(), schedule(), null, "recurrence-key")
        val stale = resource.define(requestId.toString(), schedule(), staleETag, "recurrence-key")
        val current = resource.define(requestId.toString(), schedule(), readETag, "recurrence-key")

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
    fun `refusals keep their status and reason code on every recurrence route`() {
        assertRefusedEverywhere(
            ForbiddenException("Access denied"),
            Response.Status.FORBIDDEN.statusCode,
            InformationRequestErrorCatalog.FORBIDDEN
        )
        assertRefusedEverywhere(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Recurrence not found"),
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
                InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
                "This request already recurs"
            )
        )
            .whenever(followUps).defineRecurrence(any())
        doThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.RECURRENCE_NOT_DUE,
                "The next occurrence of this recurrence is not due"
            )
        )
            .whenever(followUps).createNextOccurrence(any())
        val alreadyRecurring = routes().getValue("define")()
        val notDue = routes().getValue("createNextOccurrence")()

        assertEquals(Response.Status.CONFLICT.statusCode, alreadyRecurring.status)
        assertEquals(
            InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
            (alreadyRecurring.entity as ResponseError).reasonCode
        )
        assertEquals(Response.Status.CONFLICT.statusCode, notDue.status)
        assertEquals(InformationRequestErrorCatalog.RECURRENCE_NOT_DUE, (notDue.entity as ResponseError).reasonCode)
    }

    @Test
    fun `a plan refusal on any recurrence route is rethrown for the subscription mapper`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
                planCode = PlanCode.FREE,
                ownerType = SubscriptionOwnerType.USER,
                message = "Recurring requests are not included in this plan",
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
        doThrow(exception).whenever(followUps).defineRecurrence(any())
        doThrow(exception).whenever(followUps).createNextOccurrence(any())
    }

    private fun routes(): Map<String, () -> Response> = mapOf(
        "define" to { resource.define(requestId.toString(), schedule(), readETag, "recurrence-key") },
        "createNextOccurrence" to {
            resource.createNextOccurrence(
                requestId.toString(),
                recurrenceId.toString(),
                "occurrence-key"
            )
        },
    )

    private fun schedule() = DefineInformationRequestRecurrenceRequest(
        intervalUnit = InformationRequestRecurrenceUnit.MONTH,
        intervalCount = 3,
        firstDueAt = " 2026-11-02T09:00:00Z ",
        maximumOccurrences = 4,
    )

    private fun carryForward(decision: InformationRequestCarryForwardDecision) =
        InformationRequestCarryForward().apply {
            lineageId = lineage.id
            informationRequestId = successor.id
            informationRequestRequirementId = UUID.randomUUID()
            sourcePackageId = UUID.randomUUID()
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
