package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestClockDueEffect
import com.docuhyphen.app.api.model.entity.InformationRequestClockPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestClockPolicyVersion
import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.clock.DefineInformationRequestClockPolicyCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyDefinition
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestWorkingPeriod
import com.docuhyphen.app.api.model.informationrequest.clock.PublishInformationRequestClockPolicyVersionCommand
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockPolicyHolidayRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockPolicyPeriodRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockPolicyReminderRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockPolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockPolicyVersionRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

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
    private val service = InformationRequestClockPolicyService(
        ownerAccess, policies, versions, periods, holidays, reminders, audit, mock(),
        Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `an authorized owner defines a policy whose first version freezes its calendar and reminders`()
    {
        givenAuthorized()
        whenever(policies.save(any())).doAnswer { it.getArgument(0) }
        whenever(versions.save(any())).doAnswer { it.getArgument(0) }

        service.define(DefineInformationRequestClockPolicyCommand("Response-Window", "Response window", definition()))

        val saved = argumentCaptor<InformationRequestClockPolicy>()
        verify(policies).save(saved.capture())
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
    fun `an invalid calendar, a taken key, and an unauthorized caller are refused before anything is stored`()
    {
        givenAuthorized()
        assertThrows<InformationRequestCommandRequestException> {
            service.define(DefineInformationRequestClockPolicyCommand("window", "Window", definition(timezone = "Not/AZone")))
        }
        assertThrows<InformationRequestCommandRequestException> {
            service.define(DefineInformationRequestClockPolicyCommand("window", "Window", definition(periods = emptyList())))
        }
        assertThrows<InformationRequestCommandRequestException> {
            service.define(
                DefineInformationRequestClockPolicyCommand(
                    "window", "Window",
                    definition(periods = listOf(period(DayOfWeek.MONDAY, 540, 720), period(DayOfWeek.MONDAY, 700, 900))),
                ),
            )
        }
        assertThrows<InformationRequestCommandRequestException> {
            service.define(DefineInformationRequestClockPolicyCommand("window", "Window", definition(urgent = 3000)))
        }
        assertThrows<InformationRequestCommandRequestException> {
            service.define(DefineInformationRequestClockPolicyCommand("window", "Window", definition(reminders = listOf(60, 60))))
        }
        whenever(policies.findByKey(InformationRequestOwnerType.ORGANIZATION, organizationId, "window"))
            .thenReturn(InformationRequestClockPolicy())
        val taken = assertThrows<InformationRequestLifecycleException> {
            service.define(DefineInformationRequestClockPolicyCommand("window", "Window", definition()))
        }
        assertEquals(InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN, taken.reasonCode)

        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)).thenThrow(ForbiddenException("denied"))
        assertThrows<ForbiddenException> {
            service.define(DefineInformationRequestClockPolicyCommand("other", "Other", definition()))
        }
        verify(policies, never()).save(any())
    }

    @Test
    fun `a new version is numbered after the last one of its policy`()
    {
        givenAuthorized()
        val policyId = UUID.randomUUID()
        whenever(policies.findById(policyId)).thenReturn(
            InformationRequestClockPolicy().apply {
                id = policyId
                ownerType = InformationRequestOwnerType.ORGANIZATION
                ownerOrganizationId = organizationId
                policyKey = "window"
                displayName = "Window"
                createdByPrincipalId = userId
            },
        )
        whenever(versions.nextVersionNumber(policyId)).thenReturn(3)
        whenever(versions.save(any())).doAnswer { it.getArgument(0) }

        service.publishVersion(PublishInformationRequestClockPolicyVersionCommand(policyId, definition()))

        val version = argumentCaptor<InformationRequestClockPolicyVersion>()
        verify(versions).save(version.capture())
        assertEquals(3, version.firstValue.versionNumber)
    }

    private fun givenAuthorized()
    {
        whenever(ownerAccess.currentOwner()).thenReturn(owner)
        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)).thenReturn(PrincipalRef.user(userId))
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
