package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestSuccessorResourceOperations
import com.docuhyphen.app.api.resource.model.CreateInformationRequestSuccessorRequest
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
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestSuccessorService
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

class InformationRequestSuccessorResourceContractTest {
    private val requestId = UUID.randomUUID()
    private val priorRequestId = UUID.randomUUID()
    private val packageId = UUID.randomUUID()
    private val targetVersionId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "coordinator"))
    private val source = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        state = InformationRequestState.SUPERSEDED
        aggregateRevision = 12
    }
    private val readETag = "\"aggregate:$requestId:11\""
    private val staleETag = "\"aggregate:$requestId:10\""
    private val successor = InformationRequest().apply {
        exchangeId = source.exchangeId
        templateVersionId = targetVersionId
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = source.ownerOrganizationId
    }
    private val successorETag = InformationRequestETag.aggregateOf(successor)
    private val priorLineage = InformationRequestLineage().apply {
        successorRequestId = requestId
        sourceRequestId = priorRequestId
        lineageKind = InformationRequestLineageKind.SUPPLEMENT
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = access.principal.id
        createdAt = Timestamp.from(Instant.parse("2026-09-15T09:00:00Z"))
    }
    private val supersedingLineage = InformationRequestLineage().apply {
        successorRequestId = successor.id
        sourceRequestId = requestId
        sourcePackageId = packageId
        lineageKind = InformationRequestLineageKind.SUPERSEDING
        reasonCode = "requirements_changed"
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = access.principal.id
        createdAt = Timestamp.from(Instant.parse("2026-10-06T09:00:00Z"))
    }
    private val recurrence = InformationRequestRecurrence().apply {
        originRequestId = requestId
        intervalUnit = InformationRequestRecurrenceUnit.WEEK
        intervalCount = 2
        firstDueAt = Timestamp.from(Instant.parse("2026-10-20T09:00:00Z"))
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = access.principal.id
        createdAt = Timestamp.from(Instant.parse("2026-10-01T09:00:00Z"))
    }
    private val rule = InformationRequestRefreshRule().apply {
        informationRequestId = requestId
        requirementKey = "periodic-document"
        leadDays = 14
        createdByPrincipalKind = PrincipalKind.USER
        createdByPrincipalId = access.principal.id
        createdAt = Timestamp.from(Instant.parse("2026-10-01T09:30:00Z"))
    }
    private val created = InformationRequestSuccessorResult(
        source = source,
        successor = successor,
        lineage = supersedingLineage,
        carryForwards = listOf(
            InformationRequestCarryForward().apply {
                lineageId = supersedingLineage.id
                informationRequestId = successor.id
                informationRequestRequirementId = UUID.randomUUID()
                sourcePackageId = packageId
                sourceItemId = UUID.randomUUID()
                decision = InformationRequestCarryForwardDecision.OFFERED
            },
        ),
        successorETag = successorETag,
    )
    private val successors = mock<InformationRequestSuccessorService>()
    private val lineageQueries = mock<InformationRequestLineageQueryService>()
    private val authorizationService = mock<AuthorizationService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory> {
        on { currentAuthenticated() } doReturn access
    }
    private val resource = InformationRequestSuccessorResource(
        successors,
        lineageQueries,
        accessContextFactory,
        InformationRequestHeaderProjectionService(authorizationService, ExecutionStandingTestDoubles.active()),
    )

    init {
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
    }

    @Test
    fun `successor routes are declared only on the operations interface`() {
        val operations = InformationRequestSuccessorResourceOperations::class.java
        val methods = operations.declaredMethods.associateBy { it.name }

        assertEquals("/information-requests/{id}/successors", operations.getAnnotation(Path::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Produces::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Consumes::class.java).value)
        assertEquals(setOf("lineage", "create"), methods.keys)
        assertTrue(methods.getValue("lineage").isAnnotationPresent(GET::class.java))
        assertNull(methods.getValue("lineage").getAnnotation(Path::class.java))
        assertEquals(listOf("path:id"), parametersOf(methods.getValue("lineage")))
        assertTrue(methods.getValue("create").isAnnotationPresent(POST::class.java))
        assertNull(methods.getValue("create").getAnnotation(Path::class.java))
        assertEquals(
            listOf(
                "path:id",
                "body",
                "header:${HttpHeaders.IF_MATCH}",
                "header:${InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER}"
            ),
            parametersOf(methods.getValue("create")),
        )
        assertTrue(operations.isAssignableFrom(InformationRequestSuccessorResource::class.java))
        assertEquals(emptyList<String>(), jaxRsAnnotationsOn(InformationRequestSuccessorResource::class.java))
    }

    @Test
    fun `the lineage answers the request's source, successors, recurrence schedule, and refresh rules`() {
        whenever(lineageQueries.lineage(requestId, access)).thenReturn(
            InformationRequestLineageView(
                request = source,
                source = priorLineage,
                successors = listOf(supersedingLineage),
                recurrence = recurrence,
                nextOccurrenceDueAt = Instant.parse("2026-11-03T09:00:00Z"),
                refreshRules = listOf(InformationRequestRefreshRuleStanding(rule)),
            ),
        )

        val response = resource.lineage(requestId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(
            InformationRequestLineageViewDto(
                informationRequestId = requestId,
                source = InformationRequestLineageDto(
                    id = priorLineage.id,
                    lineageKind = InformationRequestLineageKind.SUPPLEMENT,
                    sourceRequestId = priorRequestId,
                    successorRequestId = requestId,
                    createdAt = priorLineage.createdAt,
                ),
                successors = listOf(supersedingLineageDto()),
                recurrence = InformationRequestRecurrenceDto(
                    id = recurrence.id,
                    originRequestId = requestId,
                    intervalUnit = InformationRequestRecurrenceUnit.WEEK,
                    intervalCount = 2,
                    firstDueAt = recurrence.firstDueAt,
                    createdAt = recurrence.createdAt,
                ),
                nextOccurrenceDueAt = Timestamp.from(Instant.parse("2026-11-03T09:00:00Z")),
                refreshRules = listOf(
                    InformationRequestRefreshRuleDto(
                        id = rule.id,
                        informationRequestId = requestId,
                        requirementKey = "periodic-document",
                        leadDays = 14,
                        createdAt = rule.createdAt,
                    ),
                ),
            ),
            response.entity,
        )
        verifyNoInteractions(successors)
    }

    @Test
    fun `the lineage answers a finished series as every occurrence created without a next due time`() {
        whenever(lineageQueries.lineage(requestId, access)).thenReturn(
            InformationRequestLineageView(
                request = source,
                source = null,
                successors = emptyList(),
                recurrence = recurrence,
                everyOccurrenceCreated = true,
            ),
        )

        val response = resource.lineage(requestId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        val view = response.entity as InformationRequestLineageViewDto
        assertEquals(recurrence.id, view.recurrence?.id)
        assertNull(view.nextOccurrenceDueAt)
        assertTrue(view.everyOccurrenceCreated)
    }

    @Test
    fun `creating a follow-up passes the trimmed reason, precondition, and key and answers 201 with the successor ETag`() {
        whenever(successors.create(any())).thenReturn(created)

        val response = resource.create(
            requestId.toString(),
            CreateInformationRequestSuccessorRequest(
                kind = InformationRequestLineageKind.SUPERSEDING,
                targetTemplateVersionId = targetVersionId,
                sourcePackageId = packageId,
                reasonCode = " requirements_changed ",
            ),
            readETag,
            " successor-key ",
        )

        verify(successors).create(
            CreateInformationRequestSuccessorCommand(
                sourceRequestId = requestId,
                kind = InformationRequestLineageKind.SUPERSEDING,
                targetTemplateVersionId = targetVersionId,
                sourcePackageId = packageId,
                reasonCode = "requirements_changed",
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(readETag),
                idempotencyKey = "successor-key",
            ),
        )
        assertEquals(Response.Status.CREATED.statusCode, response.status)
        assertEquals(successorETag, response.getHeaderString(HttpHeaders.ETAG))
        assertEquals(
            InformationRequestSuccessorResultDto(
                sourceRequestId = requestId,
                sourceState = InformationRequestState.SUPERSEDED,
                successor = InformationRequestDtoMapper.toDto(
                    successor,
                    true,
                    InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE),
                ),
                successorETag = successorETag,
                lineage = supersedingLineageDto(),
                offeredCount = 1,
                invalidatedCount = 0,
            ),
            response.entity,
        )
    }

    @Test
    fun `a blank reason reaches the follow-up service as no reason`() {
        whenever(successors.create(any())).thenReturn(created)

        resource.create(
            requestId.toString(),
            CreateInformationRequestSuccessorRequest(
                kind = InformationRequestLineageKind.SUPPLEMENT,
                reasonCode = "   "
            ),
            readETag,
            "supplement-key",
        )

        verify(successors).create(
            CreateInformationRequestSuccessorCommand(
                sourceRequestId = requestId,
                kind = InformationRequestLineageKind.SUPPLEMENT,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(readETag),
                idempotencyKey = "supplement-key",
            ),
        )
    }

    @Test
    fun `malformed ids and a missing key are refused before any lineage service runs`() {
        val refusals = mapOf(
            "Invalid information request id" to listOf(
                resource.lineage("not-a-uuid"),
                resource.create("not-a-uuid", followUpRequest(), readETag, "successor-key"),
            ),
            "Idempotency-Key is required" to listOf(
                resource.create(requestId.toString(), followUpRequest(), readETag, null),
                resource.create(requestId.toString(), followUpRequest(), readETag, "  "),
            ),
        )

        refusals.forEach { (message, responses) ->
            responses.forEach { response ->
                assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status, message)
                assertEquals(message, (response.entity as ResponseError).errorMessage)
            }
        }
        verifyNoInteractions(successors, lineageQueries, authorizationService)
    }

    @Test
    fun `a follow-up without a body is refused before the successor service runs`() {
        val response = resource.create(requestId.toString(), null, readETag, "successor-key")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        assertEquals("A follow-up request states its kind", (response.entity as ResponseError).errorMessage)
        verifyNoInteractions(successors)
    }

    @Test
    fun `a follow-up without If-Match is answered 428 and a stale one 412, both with the current request ETag`() {
        whenever(successors.create(any())).thenAnswer { invocation ->
            invocation.getArgument<CreateInformationRequestSuccessorCommand>(0).precondition.requireSatisfiedBy(readETag)
            created
        }

        val missing = resource.create(requestId.toString(), followUpRequest(), null, "successor-key")
        val stale = resource.create(requestId.toString(), followUpRequest(), staleETag, "successor-key")
        val current = resource.create(requestId.toString(), followUpRequest(), readETag, "successor-key")

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
    }

    @Test
    fun `refusals keep their status and reason code on every successor route`() {
        assertRefusedEverywhere(
            ForbiddenException("Access denied"),
            Response.Status.FORBIDDEN.statusCode,
            InformationRequestErrorCatalog.FORBIDDEN
        )
        assertRefusedEverywhere(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            ),
            Response.Status.NOT_FOUND.statusCode,
            InformationRequestErrorCatalog.NOT_FOUND,
        )
        assertRefusedEverywhere(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
                "A recurring or refreshing request is created from its recurrence or refresh rule",
            ),
            Response.Status.CONFLICT.statusCode,
            InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID,
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

        doThrow(CommandReceiptConflictException()).whenever(successors).create(any())
        val reusedKey = routes().getValue("create")()
        doThrow(
            InformationRequestCommandRequestException(
                "A reason code is up to 128 lowercase letters, digits, dots, dashes, or underscores",
                InformationRequestErrorCatalog.REASON_CODE_INVALID,
            ),
        ).whenever(successors).create(any())
        val unreadableReason = routes().getValue("create")()

        assertEquals(Response.Status.CONFLICT.statusCode, reusedKey.status)
        assertEquals("COMMAND_RECEIPT_FINGERPRINT_CONFLICT", (reusedKey.entity as ResponseError).reasonCode)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, unreadableReason.status)
        assertEquals(
            InformationRequestErrorCatalog.REASON_CODE_INVALID,
            (unreadableReason.entity as ResponseError).reasonCode
        )
    }

    @Test
    fun `a plan refusal on any successor route is rethrown for the subscription mapper`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.PLAN_LIMIT_REACHED,
                planCode = PlanCode.PERSONAL,
                ownerType = SubscriptionOwnerType.USER,
                message = "The plan's request allowance is used up",
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
        doThrow(exception).whenever(lineageQueries).lineage(any(), any())
        doThrow(exception).whenever(successors).create(any())
    }

    private fun routes(): Map<String, () -> Response> = mapOf(
        "lineage" to { resource.lineage(requestId.toString()) },
        "create" to { resource.create(requestId.toString(), followUpRequest(), readETag, "successor-key") },
    )

    private fun followUpRequest() = CreateInformationRequestSuccessorRequest(
        kind = InformationRequestLineageKind.SUPERSEDING,
        targetTemplateVersionId = targetVersionId,
        reasonCode = "requirements_changed",
    )

    private fun supersedingLineageDto() = InformationRequestLineageDto(
        id = supersedingLineage.id,
        lineageKind = InformationRequestLineageKind.SUPERSEDING,
        sourceRequestId = requestId,
        successorRequestId = successor.id,
        sourcePackageId = packageId,
        reasonCode = "requirements_changed",
        createdAt = supersedingLineage.createdAt,
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
