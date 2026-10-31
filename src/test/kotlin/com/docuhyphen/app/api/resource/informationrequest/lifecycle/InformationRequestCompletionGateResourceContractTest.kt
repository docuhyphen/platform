package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.dto.InformationRequestExecutionStandingDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.ChangeInformationRequestCompletionGateCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestLifecycleResult
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestCompletionGateResourceOperations
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestCompletionGateRequest
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
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestCompletionGateService
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

class InformationRequestCompletionGateResourceContractTest {
    private val requestId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "coordinator"))
    private val request = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        state = InformationRequestState.ISSUED
        gatesExchangeClosure = false
        aggregateRevision = 5
        issuedAt = Timestamp.from(Instant.parse("2026-10-01T09:00:00Z"))
        createdAt = Timestamp.from(Instant.parse("2026-09-30T09:00:00Z"))
        updatedAt = Timestamp.from(Instant.parse("2026-10-06T09:00:00Z"))
    }
    private val requestETag = InformationRequestETag.aggregateOf(request)
    private val readETag = "\"aggregate:$requestId:4\""
    private val completionGates = mock<InformationRequestCompletionGateService>()
    private val authorizationService = mock<AuthorizationService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory> {
        on { currentAuthenticated() } doReturn access
    }
    private val resource = InformationRequestCompletionGateResource(
        completionGates,
        accessContextFactory,
        InformationRequestHeaderProjectionService(authorizationService, ExecutionStandingTestDoubles.active()),
    )

    init {
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
    }

    @Test
    fun `the completion gate is a replaceable sub-resource declared only on the operations interface`() {
        val operations = InformationRequestCompletionGateResourceOperations::class.java
        val change = operations.declaredMethods.single()

        assertEquals("/information-requests/{id}/completion-gate", operations.getAnnotation(Path::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Produces::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Consumes::class.java).value)
        assertEquals("change", change.name)
        assertTrue(change.isAnnotationPresent(PUT::class.java))
        assertNull(change.getAnnotation(Path::class.java))
        assertEquals(
            listOf(
                "path:id",
                "body",
                "header:${HttpHeaders.IF_MATCH}",
                "header:${InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER}"
            ),
            parametersOf(change),
        )
        assertTrue(operations.isAssignableFrom(InformationRequestCompletionGateResource::class.java))
        assertEquals(emptyList<String>(), jaxRsAnnotationsOn(InformationRequestCompletionGateResource::class.java))
    }

    @Test
    fun `changing the gate passes the choice, caller, precondition, and key and answers the request header with its new ETag`() {
        whenever(completionGates.change(any())).thenReturn(InformationRequestLifecycleResult(request, requestETag))

        val response = resource.change(
            requestId.toString(),
            ChangeInformationRequestCompletionGateRequest(false),
            readETag,
            " gate-key "
        )

        verify(completionGates).change(
            ChangeInformationRequestCompletionGateCommand(
                requestId = requestId,
                gatesExchangeClosure = false,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(readETag),
                idempotencyKey = "gate-key",
            ),
        )
        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(requestETag, response.getHeaderString(HttpHeaders.ETAG))
        assertEquals(
            InformationRequestDto(
                id = requestId,
                exchangeId = request.exchangeId,
                templateVersionId = request.templateVersionId,
                ownerType = InformationRequestOwnerType.ORGANIZATION,
                ownerOrganizationId = request.ownerOrganizationId,
                state = InformationRequestState.ISSUED,
                gatesExchangeClosure = false,
                aggregateRevision = 5,
                issuedAt = request.issuedAt,
                createdAt = request.createdAt,
                updatedAt = request.updatedAt,
                requestETag = requestETag,
                executionStanding = InformationRequestExecutionStandingDto(InformationRequestExecutionStandingKind.ACTIVE),
            ),
            response.entity,
        )
    }

    @Test
    fun `an absent body, a malformed id, and a missing key are refused before the gate service runs`() {
        val refusals = mapOf(
            "A completion gate states whether the request gates its Exchange" to
                    resource.change(requestId.toString(), null, readETag, "gate-key"),
            "Invalid information request id" to
                    resource.change(
                        "not-a-uuid",
                        ChangeInformationRequestCompletionGateRequest(true),
                        readETag,
                        "gate-key"
                    ),
            "Idempotency-Key is required" to
                    resource.change(
                        requestId.toString(),
                        ChangeInformationRequestCompletionGateRequest(true),
                        readETag,
                        " "
                    ),
        )

        refusals.forEach { (message, response) ->
            assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status, message)
            assertEquals(message, (response.entity as ResponseError).errorMessage)
        }
        verifyNoInteractions(completionGates, authorizationService)
    }

    @Test
    fun `a change without If-Match is answered 428 and a stale one 412, both with the current request ETag`() {
        whenever(completionGates.change(any())).thenAnswer { invocation ->
            invocation.getArgument<ChangeInformationRequestCompletionGateCommand>(0).precondition.requireSatisfiedBy(
                requestETag
            )
            InformationRequestLifecycleResult(request, requestETag)
        }

        val missing = resource.change(
            requestId.toString(),
            ChangeInformationRequestCompletionGateRequest(false),
            null,
            "gate-key"
        )
        val stale = resource.change(
            requestId.toString(),
            ChangeInformationRequestCompletionGateRequest(false),
            readETag,
            "gate-key"
        )
        val current = resource.change(
            requestId.toString(),
            ChangeInformationRequestCompletionGateRequest(false),
            requestETag,
            "gate-key"
        )

        assertEquals(428, missing.status)
        assertEquals(
            CommandPreconditionException.Kind.REQUIRED.reasonCode,
            (missing.entity as ResponseError).reasonCode
        )
        assertEquals(requestETag, missing.getHeaderString(HttpHeaders.ETAG))
        assertEquals(Response.Status.PRECONDITION_FAILED.statusCode, stale.status)
        assertEquals(CommandPreconditionException.Kind.STALE.reasonCode, (stale.entity as ResponseError).reasonCode)
        assertEquals(requestETag, stale.getHeaderString(HttpHeaders.ETAG))
        assertEquals(Response.Status.OK.statusCode, current.status)
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
                InformationRequestErrorCatalog.STATE_INVALID,
                "Information Request lifecycle mutation is not allowed"
            ),
            Response.Status.CONFLICT.statusCode,
            InformationRequestErrorCatalog.STATE_INVALID,
        )
        assertRefused(
            CommandReceiptConflictException(),
            Response.Status.CONFLICT.statusCode,
            "COMMAND_RECEIPT_FINGERPRINT_CONFLICT"
        )
        assertRefused(RuntimeException("connection reset"), Response.Status.INTERNAL_SERVER_ERROR.statusCode, null)
        assertEquals("Request failed", (change().entity as ResponseError).errorMessage)
    }

    @Test
    fun `a plan refusal is rethrown for the subscription mapper`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.SUBSCRIPTION_PAST_DUE,
                planCode = PlanCode.BUSINESS,
                ownerType = SubscriptionOwnerType.ORGANIZATION,
                message = "The owner's subscription is past due",
            ),
        )
        doThrow(denial).whenever(completionGates).change(any())

        assertSame(denial, assertThrows<SubscriptionDenialException> { change() })
    }

    private fun assertRefused(exception: RuntimeException, status: Int, reasonCode: String?) {
        doThrow(exception).whenever(completionGates).change(any())

        val response = change()

        assertEquals(status, response.status, exception.javaClass.simpleName)
        assertEquals(reasonCode, (response.entity as ResponseError).reasonCode)
    }

    private fun change() =
        resource.change(
            requestId.toString(),
            ChangeInformationRequestCompletionGateRequest(false),
            readETag,
            "gate-key"
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
