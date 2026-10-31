package com.docuhyphen.app.api.resource.informationrequest.clock

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.InformationRequestClockPolicyDto
import com.docuhyphen.app.api.model.dto.InformationRequestClockPolicyVersionDto
import com.docuhyphen.app.api.model.dto.InformationRequestWorkingPeriodDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.clock.*
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.clock.operations.InformationRequestClockPolicyResourceOperations
import com.docuhyphen.app.api.resource.model.DefineInformationRequestClockPolicyRequest
import com.docuhyphen.app.api.resource.model.InformationRequestClockPolicyDefinitionRequest
import com.docuhyphen.app.api.resource.model.InformationRequestWorkingPeriodRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.*
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class InformationRequestClockPolicyResourceContractTest {
    private val policies = mock<InformationRequestClockPolicyService>()
    private val clocks = mock<InformationRequestClockService>()
    private val resource = InformationRequestClockPolicyResource(policies, clocks)
    private val policyId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val reminderCommunication = UUID.randomUUID()
    private val publishedAt = Timestamp.from(Instant.parse("2026-09-01T08:00:00Z"))

    @Test
    fun `defining and publishing take their Idempotency-Key from the header`() {
        listOf("define", "publishVersion").forEach { name ->
            val method =
                InformationRequestClockPolicyResourceOperations::class.java.declaredMethods.single { it.name == name }
            assertTrue(
                method.parameterAnnotations.any { annotations ->
                    annotations.any { it is HeaderParam && it.value == InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER }
                },
                name,
            )
        }
    }

    @Test
    fun `a definition and a publication pass their key to the service and are refused without one`() {
        whenever(policies.define(any())).thenReturn(view())
        whenever(policies.publishVersion(any())).thenReturn(view())

        assertEquals(Response.Status.CREATED.statusCode, resource.define(defineRequest(), "define-key").status)
        assertEquals(
            Response.Status.CREATED.statusCode,
            resource.publishVersion(policyId.toString(), definition(), "publish-key").status
        )
        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.define(defineRequest(), " ").status)
        assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            resource.publishVersion(policyId.toString(), definition(), null).status
        )

        val defined =
            argumentCaptor<DefineInformationRequestClockPolicyCommand>().also { verify(policies).define(it.capture()) }.firstValue
        val published = argumentCaptor<PublishInformationRequestClockPolicyVersionCommand>()
            .also { verify(policies).publishVersion(it.capture()) }.firstValue
        assertEquals("define-key", defined.idempotencyKey)
        assertEquals("publish-key", published.idempotencyKey)
        assertEquals(policyId, published.policyId)
    }

    @Test
    fun `a key another definition took is a conflict with its stable code`() {
        whenever(policies.define(any())).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN, "taken"),
        )

        val response = resource.define(defineRequest(), "define-taken")

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals(
            InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN,
            (response.entity as ResponseError).reasonCode
        )
        verify(policies, never()).publishVersion(any())
    }

    @Test
    fun `clock policy routes are declared only on the operations interface`() {
        val operations = InformationRequestClockPolicyResourceOperations::class.java
        val methods = operations.declaredMethods.associateBy { it.name }
        val key = "header:${InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER}"

        assertEquals("/information-request-clock-policies", operations.getAnnotation(Path::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Produces::class.java).value)
        assertArrayEquals(arrayOf(MediaType.APPLICATION_JSON), operations.getAnnotation(Consumes::class.java).value)
        assertEquals(setOf("list", "get", "define", "publishVersion"), methods.keys)
        assertTrue(methods.getValue("list").isAnnotationPresent(GET::class.java))
        assertNull(methods.getValue("list").getAnnotation(Path::class.java))
        assertEquals(emptyList<String>(), parametersOf(methods.getValue("list")))
        assertTrue(methods.getValue("get").isAnnotationPresent(GET::class.java))
        assertEquals("/{policyId}", methods.getValue("get").getAnnotation(Path::class.java).value)
        assertEquals(listOf("path:policyId"), parametersOf(methods.getValue("get")))
        assertTrue(methods.getValue("define").isAnnotationPresent(POST::class.java))
        assertNull(methods.getValue("define").getAnnotation(Path::class.java))
        assertEquals(listOf("body", key), parametersOf(methods.getValue("define")))
        assertTrue(methods.getValue("publishVersion").isAnnotationPresent(POST::class.java))
        assertEquals("/{policyId}/versions", methods.getValue("publishVersion").getAnnotation(Path::class.java).value)
        assertEquals(listOf("path:policyId", "body", key), parametersOf(methods.getValue("publishVersion")))
        assertTrue(operations.isAssignableFrom(InformationRequestClockPolicyResource::class.java))
        assertEquals(emptyList<String>(), jaxRsAnnotationsOn(InformationRequestClockPolicyResource::class.java))
    }

    @Test
    fun `listing and reading answer each policy with its published versions`() {
        whenever(policies.list()).thenReturn(listOf(publishedView()))
        whenever(policies.get(policyId)).thenReturn(publishedView())

        val listed = resource.list()
        val read = resource.get(policyId.toString())

        assertEquals(Response.Status.OK.statusCode, listed.status)
        @Suppress("UNCHECKED_CAST")
        val listedPolicies = (listed.entity as Array<InformationRequestClockPolicyDto>).toList()
        assertEquals(listOf(publishedDto()), listedPolicies)
        assertEquals(Response.Status.OK.statusCode, read.status)
        assertEquals(publishedDto(), read.entity)
        verifyNoInteractions(clocks)
    }

    @Test
    fun `a definition is parsed into the policy command and answered 201 with the policy`() {
        whenever(policies.define(any())).thenReturn(publishedView())
        whenever(policies.publishVersion(any())).thenReturn(publishedView())
        val requested = InformationRequestClockPolicyDefinitionRequest(
            clockType = InformationRequestClockType.BUSINESS,
            businessTimezone = "Africa/Johannesburg",
            workingPeriods = listOf(InformationRequestWorkingPeriodRequest(" monday ", 540, 1020)),
            holidays = listOf(" 2026-12-25 "),
            standardDurationMinutes = 2400,
            urgentDurationMinutes = 480,
            reminderMinutesBeforeDue = listOf(240, 60),
            escalationAfterMinutes = 600,
            dueEffect = InformationRequestClockDueEffect.EXPIRE_REQUEST,
            reminderCommunicationId = reminderCommunication,
        )
        val parsed = InformationRequestClockPolicyDefinition(
            clockType = InformationRequestClockType.BUSINESS,
            businessTimezone = "Africa/Johannesburg",
            workingPeriods = listOf(InformationRequestWorkingPeriod(DayOfWeek.MONDAY, 540, 1020)),
            holidays = listOf(LocalDate.parse("2026-12-25")),
            standardDurationMinutes = 2400,
            urgentDurationMinutes = 480,
            reminderMinutesBeforeDue = listOf(240, 60),
            escalationAfterMinutes = 600,
            dueEffect = InformationRequestClockDueEffect.EXPIRE_REQUEST,
            reminderCommunicationId = reminderCommunication,
        )

        val defined = resource.define(
            DefineInformationRequestClockPolicyRequest("response-window", "Response window", requested),
            " define-key ",
        )
        val published = resource.publishVersion(policyId.toString(), requested, "publish-key")

        verify(policies).define(
            DefineInformationRequestClockPolicyCommand(
                "response-window",
                "Response window",
                parsed,
                "define-key"
            )
        )
        verify(policies).publishVersion(
            PublishInformationRequestClockPolicyVersionCommand(
                policyId,
                parsed,
                "publish-key"
            )
        )
        listOf(defined, published).forEach { response ->
            assertEquals(Response.Status.CREATED.statusCode, response.status)
            assertEquals(publishedDto(), response.entity)
        }
    }

    @Test
    fun `malformed ids, absent bodies, and unreadable calendars are refused before the policy service runs`() {
        val unreadableDay =
            definition().copy(workingPeriods = listOf(InformationRequestWorkingPeriodRequest("someday", 540, 1020)))
        val unreadableHoliday = definition().copy(holidays = listOf("25/12/2026"))
        val refusals = mapOf(
            "Invalid clock policy id" to listOf(
                resource.get("not-a-uuid"),
                resource.publishVersion("not-a-uuid", definition(), "publish-key"),
            ),
            "A clock policy states its key, name, and definition" to listOf(resource.define(null, "define-key")),
            "A clock policy version states its definition" to listOf(
                resource.publishVersion(
                    policyId.toString(),
                    null,
                    "publish-key"
                )
            ),
            "A working period names a day of the week" to listOf(
                resource.define(defineRequest().copy(definition = unreadableDay), "define-key"),
                resource.publishVersion(policyId.toString(), unreadableDay, "publish-key"),
            ),
            "A holiday is an ISO calendar date" to listOf(
                resource.define(defineRequest().copy(definition = unreadableHoliday), "define-key"),
                resource.publishVersion(policyId.toString(), unreadableHoliday, "publish-key"),
            ),
        )

        refusals.forEach { (message, responses) ->
            responses.forEach { response ->
                assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status, message)
                assertEquals(message, (response.entity as ResponseError).errorMessage)
            }
        }
        verifyNoInteractions(policies, clocks)
    }

    @Test
    fun `refusals keep their status and reason code on every clock policy route`() {
        assertRefusedEverywhere(
            ForbiddenException("Access denied"),
            Response.Status.FORBIDDEN.statusCode,
            InformationRequestErrorCatalog.FORBIDDEN
        )
        assertRefusedEverywhere(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Clock policy not found"),
            Response.Status.NOT_FOUND.statusCode,
            InformationRequestErrorCatalog.NOT_FOUND,
        )
        assertRefusedEverywhere(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN,
                "This clock policy key is taken"
            ),
            Response.Status.CONFLICT.statusCode,
            InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN,
        )
        assertRefusedEverywhere(
            CommandReceiptConflictException(),
            Response.Status.CONFLICT.statusCode,
            "COMMAND_RECEIPT_FINGERPRINT_CONFLICT"
        )
        assertRefusedEverywhere(
            InformationRequestCommandRequestException("A clock policy has a name"),
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
    fun `a plan refusal on any clock policy route is rethrown for the subscription mapper`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
                planCode = PlanCode.FREE,
                ownerType = SubscriptionOwnerType.ORGANIZATION,
                message = "Response clocks are not included in this plan",
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
        doThrow(exception).whenever(policies).list()
        doThrow(exception).whenever(policies).get(any())
        doThrow(exception).whenever(policies).define(any())
        doThrow(exception).whenever(policies).publishVersion(any())
    }

    private fun routes(): Map<String, () -> Response> = mapOf(
        "list" to { resource.list() },
        "get" to { resource.get(policyId.toString()) },
        "define" to { resource.define(defineRequest(), "define-key") },
        "publishVersion" to { resource.publishVersion(policyId.toString(), definition(), "publish-key") },
    )

    private fun view() = InformationRequestClockPolicyView(
        InformationRequestClockPolicy().apply {
            id = policyId
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            policyKey = "response-window"
            displayName = "Response window"
            createdByPrincipalId = UUID.randomUUID()
        },
        emptyList(),
    )

    private fun publishedView() = InformationRequestClockPolicyView(
        view().policy,
        listOf(
            InformationRequestClockPolicyVersionView(
                InformationRequestClockPolicyVersion().apply {
                    id = versionId
                    policyId = this@InformationRequestClockPolicyResourceContractTest.policyId
                    versionNumber = 2
                    clockType = InformationRequestClockType.BUSINESS
                    businessTimezone = "Africa/Johannesburg"
                    standardDurationMinutes = 2400
                    urgentDurationMinutes = 480
                    escalationAfterMinutes = 600
                    dueEffect = InformationRequestClockDueEffect.MARK_OVERDUE
                    reminderCommunicationId = reminderCommunication
                    publishedByPrincipalId = UUID.randomUUID()
                    publishedAt = this@InformationRequestClockPolicyResourceContractTest.publishedAt
                },
                InformationRequestClockCalendar(
                    InformationRequestClockType.BUSINESS,
                    ZoneId.of("Africa/Johannesburg"),
                    listOf(InformationRequestWorkingPeriod(DayOfWeek.MONDAY, 540, 1020)),
                    setOf(LocalDate.parse("2026-12-25")),
                ),
                listOf(LocalDate.parse("2026-12-25")),
                listOf(240, 60),
            ),
        ),
    )

    private fun publishedDto() = InformationRequestClockPolicyDto(
        id = policyId,
        policyKey = "response-window",
        displayName = "Response window",
        ownerType = InformationRequestOwnerType.ORGANIZATION,
        versions = listOf(
            InformationRequestClockPolicyVersionDto(
                id = versionId,
                versionNumber = 2,
                clockType = InformationRequestClockType.BUSINESS,
                businessTimezone = "Africa/Johannesburg",
                standardDurationMinutes = 2400,
                urgentDurationMinutes = 480,
                escalationAfterMinutes = 600,
                dueEffect = InformationRequestClockDueEffect.MARK_OVERDUE,
                workingPeriods = listOf(InformationRequestWorkingPeriodDto("MONDAY", 540, 1020)),
                holidays = listOf("2026-12-25"),
                reminderMinutesBeforeDue = listOf(240, 60),
                reminderCommunicationId = reminderCommunication,
                publishedAt = publishedAt,
            ),
        ),
    )

    private fun defineRequest() =
        DefineInformationRequestClockPolicyRequest("response-window", "Response window", definition())

    private fun definition() = InformationRequestClockPolicyDefinitionRequest(
        clockType = InformationRequestClockType.BUSINESS,
        businessTimezone = "Africa/Johannesburg",
        workingPeriods = listOf(InformationRequestWorkingPeriodRequest("MONDAY", 540, 1020)),
        standardDurationMinutes = 2400,
        urgentDurationMinutes = 480,
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
