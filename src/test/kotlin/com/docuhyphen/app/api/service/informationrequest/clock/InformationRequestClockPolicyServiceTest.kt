package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.clock.DefineInformationRequestClockPolicyCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyDefinition
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestWorkingPeriod
import com.docuhyphen.app.api.model.informationrequest.clock.PublishInformationRequestClockPolicyVersionCommand
import com.docuhyphen.app.api.repository.informationrequest.clock.*
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.sql.SQLException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

class InformationRequestClockPolicyServiceTest
{
    private val organizationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, organizationId)
    private val ownerAccess = mock<InformationRequestOwnerScopeAccess>()
    private val policies = mock<InformationRequestClockPolicyRepository>()
    private val versions = mock<InformationRequestClockPolicyVersionRepository>()
    private val periods = mock<InformationRequestClockPolicyPeriodRepository>()
    private val holidays = mock<InformationRequestClockPolicyHolidayRepository>()
    private val reminders = mock<InformationRequestClockPolicyReminderRepository>()
    private val audit = mock<AuditRecorder>()
    private val displays = mock<PrincipalDisplayService>()
    private val receipts = InMemoryClockPolicyReceiptStore()
    private val service = InformationRequestClockPolicyService(
        ownerAccess,
        policies,
        versions,
        periods,
        holidays,
        reminders,
        audit,
        InformationRequestAuditActorLabelResolver(displays),
        mock(),
        Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC),
        CommandReceiptService(receipts, CommandReceiptScope()),
    )

    @Test
    fun `an authorized owner defines a policy whose first version freezes its calendar and reminders`()
    {
        givenAuthorized()
        givenStored()

        service.define(
            DefineInformationRequestClockPolicyCommand(
                "Response-Window",
                "Response window",
                definition(),
                "define-window"
            )
        )

        val saved = argumentCaptor<InformationRequestClockPolicy>()
        verify(policies).insertNow(saved.capture())
        assertEquals("response-window", saved.firstValue.policyKey)
        assertEquals(organizationId, saved.firstValue.ownerOrganizationId)
        val version = argumentCaptor<InformationRequestClockPolicyVersion>()
        verify(versions).save(version.capture())
        assertEquals(1, version.firstValue.versionNumber)
        verify(periods, org.mockito.kotlin.times(5)).save(any())
        verify(reminders, org.mockito.kotlin.times(2)).save(any())
        verify(audit).record(any())
    }

    @Test
    fun `a published policy version is audited under the owner's account label and session`() {
        givenAuthorized()
        givenStored()
        whenever(displays.userLabel(userId)).thenReturn("Policy Owner <owner@process.test>")

        service.define(
            DefineInformationRequestClockPolicyCommand(
                "response-window",
                "Response window",
                definition(),
                "define-attributed"
            )
        )

        val draft = argumentCaptor<AuditEventDraft>()
        verify(audit).record(draft.capture())
        assertEquals(userId, draft.firstValue.actorId)
        assertEquals(AuditActorKind.HUMAN, draft.firstValue.actorKind)
        assertEquals("Policy Owner <owner@process.test>", draft.firstValue.actorLabel)
        assertEquals("owner-session", draft.firstValue.sessionId)
    }

    @Test
    fun `an invalid calendar, a taken key, and an unauthorized caller are refused before anything is stored`()
    {
        givenAuthorized()
        assertThrows<InformationRequestCommandRequestException> {
            service.define(
                DefineInformationRequestClockPolicyCommand(
                    "window",
                    "Window",
                    definition(timezone = "Not/AZone"),
                    "bad-zone"
                )
            )
        }
        assertThrows<InformationRequestCommandRequestException> {
            service.define(
                DefineInformationRequestClockPolicyCommand(
                    "window",
                    "Window",
                    definition(periods = emptyList()),
                    "no-periods"
                )
            )
        }
        assertThrows<InformationRequestCommandRequestException> {
            service.define(
                DefineInformationRequestClockPolicyCommand(
                    "window", "Window",
                    definition(
                        periods = listOf(
                            period(DayOfWeek.MONDAY, 540, 720),
                            period(DayOfWeek.MONDAY, 700, 900)
                        )
                    ),
                    "overlapping-periods",
                ),
            )
        }
        assertThrows<InformationRequestCommandRequestException> {
            service.define(
                DefineInformationRequestClockPolicyCommand(
                    "window",
                    "Window",
                    definition(urgent = 3000),
                    "long-urgent"
                )
            )
        }
        assertThrows<InformationRequestCommandRequestException> {
            service.define(
                DefineInformationRequestClockPolicyCommand(
                    "window",
                    "Window",
                    definition(reminders = listOf(60, 60)),
                    "repeated-reminders"
                )
            )
        }
        whenever(policies.findByKey(InformationRequestOwnerType.ORGANIZATION, organizationId, "window"))
            .thenReturn(InformationRequestClockPolicy())
        val taken = assertThrows<InformationRequestLifecycleException> {
            service.define(DefineInformationRequestClockPolicyCommand("window", "Window", definition(), "taken-key"))
        }
        assertEquals(InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN, taken.reasonCode)

        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)).thenThrow(
            ForbiddenException("denied")
        )
        assertThrows<ForbiddenException> {
            service.define(DefineInformationRequestClockPolicyCommand("other", "Other", definition(), "denied"))
        }
        verify(policies, never()).insertNow(any())
        verify(policies, never()).save(any())
    }

    @Test
    fun `split, varying, adjacent, and midnight-ending working periods are accepted and stored exactly as stated`() {
        givenAuthorized()
        givenStored()
        val calendars = listOf(
            listOf(period(DayOfWeek.SUNDAY, 120, 150), period(DayOfWeek.SUNDAY, 180, 240)),
            listOf(period(DayOfWeek.MONDAY, 540, 720), period(DayOfWeek.MONDAY, 780, 1020)),
            listOf(period(DayOfWeek.MONDAY, 540, 1020), period(DayOfWeek.FRIDAY, 540, 780)),
            listOf(period(DayOfWeek.MONDAY, 0, 1440), period(DayOfWeek.TUESDAY, 1020, 1440)),
            listOf(period(DayOfWeek.WEDNESDAY, 540, 720), period(DayOfWeek.WEDNESDAY, 720, 1020)),
        )

        calendars.forEachIndexed { index, periods ->
            service.define(
                DefineInformationRequestClockPolicyCommand(
                    "window-$index",
                    "Window $index",
                    definition(timezone = "Europe/Berlin", periods = periods),
                    "calendar-$index",
                ),
            )
        }

        val stored = argumentCaptor<InformationRequestClockPolicyPeriod>()
        verify(periods, times(calendars.sumOf { it.size })).save(stored.capture())
        assertEquals(
            calendars.flatten(),
            stored.allValues.map { period(DayOfWeek.of(it.dayOfWeek.toInt()), it.startMinute, it.endMinute) },
        )
    }

    @Test
    fun `a working period that starts at the end of the day or ends after it is refused`() {
        givenAuthorized()

        listOf(period(DayOfWeek.MONDAY, 1440, 1440), period(DayOfWeek.MONDAY, 1020, 1441)).forEach { refused ->
            assertThrows<InformationRequestCommandRequestException> {
                service.define(
                    DefineInformationRequestClockPolicyCommand(
                        "window",
                        "Window",
                        definition(periods = listOf(refused)),
                        "refused-${refused.startMinute}-${refused.endMinute}",
                    ),
                )
            }
        }
        verify(periods, never()).save(any())
    }

    @Test
    fun `a retried definition replays the stored policy and a reused key with another body is refused`() {
        givenAuthorized()
        givenStored()
        val command = DefineInformationRequestClockPolicyCommand("window", "Window", definition(), "define-once")

        val defined = service.define(command)
        whenever(policies.findById(defined.policy.id)).thenReturn(defined.policy)
        val replayed = service.define(command)

        assertEquals(defined.policy.id, replayed.policy.id)
        verify(policies).insertNow(any())
        verify(versions).save(any())
        assertThrows<CommandReceiptConflictException> {
            service.define(command.copy(displayName = "Another window"))
        }
        verify(policies).insertNow(any())
    }

    @Test
    fun `a key another definition took first is refused with the key-taken code`() {
        givenAuthorized()
        whenever(policies.insertNow(any())).thenThrow(
            ConstraintViolationException(
                "duplicate key",
                SQLException("duplicate key value violates unique constraint", "23505"),
                "ux_information_request_clock_policy_organization_key",
            ),
        )

        val taken = assertThrows<InformationRequestLifecycleException> {
            service.define(DefineInformationRequestClockPolicyCommand("window", "Window", definition(), "define-raced"))
        }

        assertEquals(InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN, taken.reasonCode)
        verify(versions, never()).save(any())
    }

    @Test
    fun `publishing locks its policy before numbering the version and a retried publication replays it`()
    {
        givenAuthorized()
        val policyId = UUID.randomUUID()
        val policy = organizationPolicy(policyId)
        whenever(policies.findByIdForUpdate(policyId)).thenReturn(policy)
        whenever(policies.findById(policyId)).thenReturn(policy)
        whenever(versions.nextVersionNumber(policyId)).thenReturn(2)
        whenever(versions.save(any())).doAnswer { it.getArgument(0) }
        val command = PublishInformationRequestClockPolicyVersionCommand(policyId, definition(), "publish-once")

        service.publishVersion(command)
        service.publishVersion(command)

        val order = inOrder(policies, versions)
        order.verify(policies).findByIdForUpdate(policyId)
        order.verify(versions).nextVersionNumber(policyId)
        verify(versions).save(any())
        assertThrows<CommandReceiptConflictException> {
            service.publishVersion(command.copy(definition = definition(urgent = 240)))
        }
    }

    private fun givenStored() {
        whenever(policies.insertNow(any())).doAnswer { it.getArgument(0) }
        whenever(policies.save(any())).doAnswer { it.getArgument(0) }
        whenever(versions.save(any())).doAnswer { it.getArgument(0) }
    }

    private fun organizationPolicy(policyId: UUID) = InformationRequestClockPolicy().apply {
        id = policyId
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = organizationId
        policyKey = "window"
        displayName = "Window"
        createdByPrincipalId = userId
    }

    @Test
    fun `a new version is numbered after the last one of its policy`() {
        givenAuthorized()
        val policyId = UUID.randomUUID()
        whenever(policies.findByIdForUpdate(policyId)).thenReturn(organizationPolicy(policyId))
        whenever(policies.findById(policyId)).thenReturn(organizationPolicy(policyId))
        whenever(versions.nextVersionNumber(policyId)).thenReturn(3)
        whenever(versions.save(any())).doAnswer { it.getArgument(0) }

        service.publishVersion(
            PublishInformationRequestClockPolicyVersionCommand(
                policyId,
                definition(),
                "publish-third"
            )
        )

        val version = argumentCaptor<InformationRequestClockPolicyVersion>()
        verify(versions).save(version.capture())
        assertEquals(3, version.firstValue.versionNumber)
    }

    private fun givenAuthorized()
    {
        whenever(ownerAccess.currentOwner()).thenReturn(owner)
        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)).thenReturn(
            RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(sessionRef = "owner-session"))
        )
    }

    private fun definition(
        timezone: String = "Africa/Johannesburg",
        periods: List<InformationRequestWorkingPeriod> = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ).map { period(it, 540, 1020) },
        urgent: Int = 480,
        reminders: List<Int> = listOf(60, 480),
    ) = InformationRequestClockPolicyDefinition(
        clockType = InformationRequestClockType.BUSINESS,
        businessTimezone = timezone,
        workingPeriods = periods,
        holidays = emptyList(),
        standardDurationMinutes = 2400,
        urgentDurationMinutes = urgent,
        reminderMinutesBeforeDue = reminders,
        escalationAfterMinutes = 240,
        dueEffect = InformationRequestClockDueEffect.MARK_OVERDUE,
    )

    private fun period(day: DayOfWeek, start: Int, end: Int) = InformationRequestWorkingPeriod(day, start, end)
}

private class InMemoryClockPolicyReceiptStore : CommandReceiptStore {
    private val receipts = mutableListOf<CommandReceipt>()

    override fun lockCommand(request: CommandReceiptRequest) = Unit

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                    it.resourceId == request.resource.id &&
                    it.operationName == request.operation &&
                    it.actorKind == request.actor.kind &&
                    it.actorId == request.actor.id &&
                    it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt {
        receipts += receipt
        return receipt
    }
}
