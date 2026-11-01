package com.docuhyphen.app.api.resource.informationrequest.clock

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestClockDto
import com.docuhyphen.app.api.model.dto.InformationRequestClockEventDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.clock.*
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.clock.handler.InformationRequestClockRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.clock.operations.InformationRequestClockResourceOperations
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestClockRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.StartInformationRequestClockRequest
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.command.RevisionETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.*

class InformationRequestClockResourceContractTest {
    private val requestId = UUID.randomUUID()
    private val clockId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val access =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "coordinator"))
    private val clockETag = RevisionETag.of(clockId, 3)
    private val received = Instant.parse("2026-10-05T07:00:00Z")
    private val due = Instant.parse("2026-10-07T15:00:00Z")
    private val paused = Instant.parse("2026-10-05T12:00:00Z")
    private val policies = mock<InformationRequestClockPolicyService>()
    private val clocks = mock<InformationRequestClockService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory> {
        on { currentAuthenticated() } doReturn access
    }
    private val resource = InformationRequestClockResource(policies, clocks, accessContextFactory)
    private val clock = InformationRequestClock().apply {
        id = clockId
        informationRequestId = requestId
        clockKey = "first-response"
        policyVersionId = versionId
        urgency = InformationRequestClockUrgency.URGENT
        receivedAt = Timestamp.from(received)
        state = InformationRequestClockState.PAUSED
        dueAt = Timestamp.from(due)
        dueCycle = 1
        remainingSeconds = 3600
        clockRevision = 3
        startedByPrincipalId = access.principal.id
        startedAt = Timestamp.from(received)
    }
    private val view = InformationRequestClockView(
        clock = clock,
        policyVersion = InformationRequestClockPolicyVersionView(
            InformationRequestClockPolicyVersion().apply {
                id = versionId
                policyId = UUID.randomUUID()
                versionNumber = 2
                clockType = InformationRequestClockType.BUSINESS
                businessTimezone = "Africa/Johannesburg"
                standardDurationMinutes = 2400
                urgentDurationMinutes = 480
                publishedByPrincipalId = UUID.randomUUID()
            },
            InformationRequestClockCalendar(
                InformationRequestClockType.BUSINESS,
                ZoneId.of("Africa/Johannesburg"),
                listOf(InformationRequestWorkingPeriod(DayOfWeek.MONDAY, 540, 1020)),
                emptySet(),
            ),
            emptyList(),
            listOf(60),
        ),
        events = listOf(
            InformationRequestClockEvent().apply {
                clockId = clock.id
                informationRequestId = requestId
                eventNumber = 2
                eventKind = InformationRequestClockEventKind.PAUSED
                dueCycle = 1
                reasonCode = "waiting_on_party"
                occurredAt = Timestamp.from(paused)
                recordedByPrincipalId = access.principal.id
            },
        ),
        clockETag = clockETag,
    )
    private val clockDto = InformationRequestClockDto(
        id = clockId,
        clockKey = "first-response",
        policyVersionId = versionId,
        policyVersionNumber = 2,
        clockType = InformationRequestClockType.BUSINESS,
        urgency = InformationRequestClockUrgency.URGENT,
        receivedAt = Timestamp.from(received),
        state = InformationRequestClockState.PAUSED,
        dueAt = Timestamp.from(due),
        dueCycle = 1,
        remainingSeconds = 3600,
        clockETag = clockETag,
        events = listOf(
            InformationRequestClockEventDto(
                eventNumber = 2,
                eventKind = InformationRequestClockEventKind.PAUSED,
                dueCycle = 1,
                reasonCode = "waiting_on_party",
                occurredAt = Timestamp.from(paused),
            ),
        ),
    )

    @Test
    fun `clock routes are sub-resources of one request declared only on the operations interface`() {
        val operations = InformationRequestClockResourceOperations::class.java
        val methods = operations.declaredMethods.associateBy { it.name }
        val changeParameters =
            listOf("path:id", "path:clockId", "body", "header:${HttpHeaders.IF_MATCH}", "header:$IDEMPOTENCY_KEY")

        assertEquals("/information-requests/{id}/clocks", operations.getAnnotation(Path::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Produces::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Consumes::class.java).value)
        assertEquals(setOf("list", "start", "pause", "resume", "extend"), methods.keys)
        assertTrue(methods.getValue("list").isAnnotationPresent(GET::class.java))
        assertNull(methods.getValue("list").getAnnotation(Path::class.java))
        assertEquals(listOf("path:id"), parametersOf(methods.getValue("list")))
        assertTrue(methods.getValue("start").isAnnotationPresent(POST::class.java))
        assertNull(methods.getValue("start").getAnnotation(Path::class.java))
        assertEquals(listOf("path:id", "body", "header:$IDEMPOTENCY_KEY"), parametersOf(methods.getValue("start")))
        mapOf(
            "pause" to "/{clockId}/pauses",
            "resume" to "/{clockId}/resumptions",
            "extend" to "/{clockId}/extensions",
        ).forEach { (name, path) ->
            val method = methods.getValue(name)
            assertTrue(method.isAnnotationPresent(POST::class.java), name)
            assertEquals(path, method.getAnnotation(Path::class.java).value, name)
            assertEquals(changeParameters, parametersOf(method), name)
        }
        assertTrue(operations.isAssignableFrom(InformationRequestClockResource::class.java))
        assertEquals(emptyList<String>(), jaxRsAnnotationsOn(InformationRequestClockResource::class.java))
        assertEquals(emptyList<String>(), jaxRsAnnotationsOn(InformationRequestClockRequestHandler::class.java))
    }

    @Test
    fun `listing answers each clock of the request with its policy version, events, and clock ETag`() {
        whenever(clocks.clocks(requestId, access)).thenReturn(listOf(view))

        val response = resource.list(requestId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val listed = (response.entity as Array<InformationRequestClockDto>).toList()
        assertEquals(listOf(clockDto), listed)
        verifyNoInteractions(policies)
    }

    @Test
    fun `starting a clock passes the parsed body, caller, and key and answers 201 with the clock ETag`() {
        whenever(clocks.start(any())).thenReturn(view)

        val response = resource.start(requestId.toString(), startRequest(), " start-key ")

        verify(clocks).start(
            StartInformationRequestClockCommand(
                requestId = requestId,
                clockKey = "first-response",
                policyVersionId = versionId,
                urgency = InformationRequestClockUrgency.URGENT,
                receivedAt = received,
                access = access,
                idempotencyKey = "start-key",
            ),
        )
        assertEquals(Response.Status.CREATED.statusCode, response.status)
        assertEquals(clockETag, response.getHeaderString(HttpHeaders.ETAG))
        assertEquals(clockDto, response.entity)
    }

    @Test
    fun `pausing, resuming, and extending carry the change, reason, precondition, and key and answer 200 with the clock ETag`() {
        whenever(clocks.change(any())).thenReturn(view)

        val responses = mapOf(
            InformationRequestClockChange.PAUSE to
                    resource.pause(requestId.toString(), clockId.toString(), changeRequest(), clockETag, "pause-key"),
            InformationRequestClockChange.RESUME to
                    resource.resume(requestId.toString(), clockId.toString(), changeRequest(), clockETag, "resume-key"),
            InformationRequestClockChange.EXTEND to
                    resource.extend(
                        requestId.toString(),
                        clockId.toString(),
                        changeRequest(),
                        clockETag,
                        " extend-key "
                    ),
        )

        val commands = argumentCaptor<ChangeInformationRequestClockCommand>()
        verify(clocks, times(3)).change(commands.capture())
        assertEquals(
            listOf(
                changeCommand(InformationRequestClockChange.PAUSE, "pause-key"),
                changeCommand(InformationRequestClockChange.RESUME, "resume-key"),
                changeCommand(InformationRequestClockChange.EXTEND, "extend-key"),
            ),
            commands.allValues,
        )
        responses.forEach { (change, response) ->
            assertEquals(Response.Status.OK.statusCode, response.status, change.name)
            assertEquals(clockETag, response.getHeaderString(HttpHeaders.ETAG), change.name)
            assertEquals(clockDto, response.entity, change.name)
        }
    }

    @Test
    fun `malformed ids, absent bodies, and missing keys are refused before the clock service runs`() {
        val refusals = mapOf(
            "Invalid information request id" to listOf(
                resource.list("not-a-uuid"),
                resource.start("not-a-uuid", startRequest(), "start-key"),
                resource.pause("not-a-uuid", clockId.toString(), changeRequest(), clockETag, "pause-key"),
            ),
            "Invalid clock id" to listOf(
                resource.pause(requestId.toString(), "not-a-uuid", changeRequest(), clockETag, "pause-key"),
                resource.resume(requestId.toString(), "not-a-uuid", changeRequest(), clockETag, "resume-key"),
                resource.extend(requestId.toString(), "not-a-uuid", changeRequest(), clockETag, "extend-key"),
            ),
            "A clock names its key and policy version" to listOf(
                resource.start(requestId.toString(), null, "start-key"),
            ),
            "A clock change states its reason" to listOf(
                resource.pause(requestId.toString(), clockId.toString(), null, clockETag, "pause-key"),
                resource.resume(requestId.toString(), clockId.toString(), null, clockETag, "resume-key"),
                resource.extend(requestId.toString(), clockId.toString(), null, clockETag, "extend-key"),
            ),
            "Idempotency-Key is required" to listOf(
                resource.start(requestId.toString(), startRequest(), null),
                resource.start(requestId.toString(), startRequest(), " "),
                resource.pause(requestId.toString(), clockId.toString(), changeRequest(), clockETag, null),
                resource.resume(requestId.toString(), clockId.toString(), changeRequest(), clockETag, ""),
                resource.extend(requestId.toString(), clockId.toString(), changeRequest(), clockETag, "  "),
            ),
        )

        refusals.forEach { (message, responses) ->
            responses.forEach { response ->
                assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status, message)
                assertEquals(message, (response.entity as ResponseError).errorMessage)
            }
        }
        verifyNoInteractions(clocks, policies)
    }

    @Test
    fun `a clock change without If-Match is answered 428 and a stale one 412, both with the current clock ETag`() {
        whenever(clocks.change(any())).thenAnswer { invocation ->
            invocation.getArgument<ChangeInformationRequestClockCommand>(0).precondition.requireSatisfiedBy(clockETag)
            view
        }
        val staleETag = RevisionETag.of(clockId, 2)

        listOf<(String?) -> Response>(
            { ifMatch ->
                resource.pause(
                    requestId.toString(),
                    clockId.toString(),
                    changeRequest(),
                    ifMatch,
                    "pause-key"
                )
            },
            { ifMatch ->
                resource.resume(
                    requestId.toString(),
                    clockId.toString(),
                    changeRequest(),
                    ifMatch,
                    "resume-key"
                )
            },
            { ifMatch ->
                resource.extend(
                    requestId.toString(),
                    clockId.toString(),
                    changeRequest(),
                    ifMatch,
                    "extend-key"
                )
            },
        ).forEach { change ->
            val missing = change(null)
            val stale = change(staleETag)
            val current = change(clockETag)

            assertEquals(428, missing.status)
            assertEquals(
                CommandPreconditionException.Kind.REQUIRED.reasonCode,
                (missing.entity as ResponseError).reasonCode
            )
            assertEquals(clockETag, missing.getHeaderString(HttpHeaders.ETAG))
            assertEquals(Response.Status.PRECONDITION_FAILED.statusCode, stale.status)
            assertEquals(CommandPreconditionException.Kind.STALE.reasonCode, (stale.entity as ResponseError).reasonCode)
            assertEquals(clockETag, stale.getHeaderString(HttpHeaders.ETAG))
            assertEquals(Response.Status.OK.statusCode, current.status)
        }
    }

    @Test
    fun `refusals keep their status and reason code on every clock route`() {
        assertRefusedEverywhere(
            ForbiddenException("Access denied"),
            Response.Status.FORBIDDEN.statusCode,
            InformationRequestErrorCatalog.FORBIDDEN
        )
        assertRefusedEverywhere(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Request clock not found"),
            Response.Status.NOT_FOUND.statusCode,
            InformationRequestErrorCatalog.NOT_FOUND,
        )
        assertRefusedEverywhere(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CLOCK_STATE_INVALID,
                "The clock cannot make this change"
            ),
            Response.Status.CONFLICT.statusCode,
            InformationRequestErrorCatalog.CLOCK_STATE_INVALID,
        )
        assertRefusedEverywhere(
            CommandReceiptConflictException(),
            Response.Status.CONFLICT.statusCode,
            "COMMAND_RECEIPT_FINGERPRINT_CONFLICT"
        )
        assertRefusedEverywhere(
            InformationRequestCommandRequestException("A clock key uses lowercase letters, digits, dots, dashes, or underscores"),
            Response.Status.BAD_REQUEST.statusCode,
            null,
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
    }

    @Test
    fun `a plan refusal on any clock route is rethrown for the subscription mapper`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED,
                planCode = PlanCode.BUSINESS,
                ownerType = SubscriptionOwnerType.ORGANIZATION,
                message = "The owner's subscription is suspended",
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
        doThrow(exception).whenever(clocks).clocks(any(), any())
        doThrow(exception).whenever(clocks).start(any())
        doThrow(exception).whenever(clocks).change(any())
    }

    private fun routes(): Map<String, () -> Response> = mapOf(
        "list" to { resource.list(requestId.toString()) },
        "start" to { resource.start(requestId.toString(), startRequest(), "start-key") },
        "pause" to {
            resource.pause(
                requestId.toString(),
                clockId.toString(),
                changeRequest(),
                clockETag,
                "pause-key"
            )
        },
        "resume" to {
            resource.resume(
                requestId.toString(),
                clockId.toString(),
                changeRequest(),
                clockETag,
                "resume-key"
            )
        },
        "extend" to {
            resource.extend(
                requestId.toString(),
                clockId.toString(),
                changeRequest(),
                clockETag,
                "extend-key"
            )
        },
    )

    private fun startRequest() = StartInformationRequestClockRequest(
        clockKey = "first-response",
        policyVersionId = versionId,
        urgency = InformationRequestClockUrgency.URGENT,
        receivedAt = Timestamp.from(received),
    )

    @Test
    fun `a wildcard If-Match on a clock change is sent to the service as a missing precondition`() {
        whenever(clocks.change(any()))
            .thenThrow(CommandPreconditionException.required(clockETag))

        val response = resource.pause(requestId.toString(), clockId.toString(), changeRequest(), "*", "pause-key")

        assertEquals(428, response.status)
        verify(clocks).change(
            org.mockito.kotlin.argThat { precondition == CommandPrecondition.Absent },
        )
    }

    private fun changeRequest() =
        ChangeInformationRequestClockRequest(reasonCode = "waiting_on_party", extensionMinutes = 120)

    private fun changeCommand(change: InformationRequestClockChange, idempotencyKey: String) =
        ChangeInformationRequestClockCommand(
            requestId = requestId,
            clockId = clockId,
            change = change,
            extensionMinutes = 120,
            reasonCode = "waiting_on_party",
            access = access,
            precondition = CommandPrecondition.ExpectedRevision(clockETag),
            idempotencyKey = idempotencyKey,
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

    private companion object {
        const val IDEMPOTENCY_KEY = InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
    }
}
