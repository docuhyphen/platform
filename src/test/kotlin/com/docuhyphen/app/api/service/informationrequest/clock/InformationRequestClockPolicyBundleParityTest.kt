package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.dto.InformationRequestConfigurationBundleValidationDto
import com.docuhyphen.app.api.model.entity.InformationRequestClockDueEffect
import com.docuhyphen.app.api.model.entity.InformationRequestClockPolicyVersion
import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.clock.DefineInformationRequestClockPolicyCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockPolicyDefinition
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestWorkingPeriod
import com.docuhyphen.app.api.model.informationrequest.template.BundleClockPeriod
import com.docuhyphen.app.api.model.informationrequest.template.BundleClockPolicy
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestConfigurationBundle
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockPolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockPolicyVersionRepository
import com.docuhyphen.app.api.resource.informationrequest.template.InformationRequestConfigurationBundleResource
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestConfigurationBundleService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestConfigurationBundleValidator
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateAuthoringService
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

class InformationRequestClockPolicyBundleParityTest {
    private val organizationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, organizationId)
    private val access =
        RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(sessionRef = "policy-owner"))
    private val ownerAccess = mock<InformationRequestOwnerScopeAccess>().also {
        whenever(it.currentOwner()).thenReturn(owner)
        whenever(it.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)).thenReturn(access)
    }
    private val policies = mock<InformationRequestClockPolicyRepository>().also {
        whenever(it.insertNow(any())).doAnswer { invocation -> invocation.getArgument(0) }
    }
    private val versions = mock<InformationRequestClockPolicyVersionRepository>().also {
        whenever(it.save(any())).doAnswer { invocation -> invocation.getArgument(0) }
    }
    private val receipts = mock<CommandReceiptStore>().also {
        whenever(it.insert(any())).doAnswer { invocation -> invocation.getArgument(0) }
    }
    private val authoring = InformationRequestClockPolicyService(
        ownerAccess,
        policies,
        versions,
        mock(),
        mock(),
        mock(),
        mock(),
        InformationRequestAuditActorLabelResolver(mock()),
        mock(),
        Clock.fixed(Instant.parse("2026-10-17T08:00:00Z"), ZoneOffset.UTC),
        CommandReceiptService(receipts, CommandReceiptScope()),
    )
    private val bundles = InformationRequestConfigurationBundleResource(
        InformationRequestConfigurationBundleService(
            InformationRequestConfigurationBundleValidator(),
            mock<InformationRequestTemplateAuthoringService>().also {
                whenever(it.frozenVersionContents(any(), any())).thenReturn(emptyList())
            },
        ),
        mock<InformationRequestAccessContextFactory>().also { whenever(it.currentAuthenticated()).thenReturn(access) },
    )

    @ParameterizedTest
    @ValueSource(
        strings = [
            "positive-escalation", "disabled-escalation", "zero-escalation", "negative-escalation",
            "overlapping-periods", "overlapping-periods-out-of-order", "periods-sharing-a-start", "adjacent-periods",
            "midnight-ending-period", "inverted-period", "period-past-midnight", "period-starting-at-midnight",
            "distinct-reminders", "duplicate-reminders", "zero-reminder", "urgent-as-long-as-standard",
            "urgent-longer-than-standard", "zero-standard", "unknown-time-zone", "padded-time-zone",
            "business-without-periods", "calendar-without-periods", "calendar-with-periods",
        ],
    )
    fun `clock authoring and bundle validation reach the same verdict`(case: String) {
        val (definition, accepted) = parityCase(case)

        assertEquals(accepted, authoringAccepts(case, definition), "Clock authoring verdict for $case")
        val bundle = bundleVerdict(definition)
        assertEquals(accepted, bundle.valid, "Bundle verdict for $case: ${bundle.problems}")
    }

    @Test
    fun `an explicit zero escalation delay stays distinct from an absent one`() {
        authoring.define(
            DefineInformationRequestClockPolicyCommand(
                "immediate",
                "Immediate",
                definition().copy(escalationAfterMinutes = 0),
                "immediate"
            )
        )
        authoring.define(
            DefineInformationRequestClockPolicyCommand(
                "disabled",
                "Disabled",
                definition().copy(escalationAfterMinutes = null),
                "disabled"
            )
        )

        val stored = argumentCaptor<InformationRequestClockPolicyVersion>()
        verify(versions, times(2)).save(stored.capture())
        assertEquals(0, stored.firstValue.escalationAfterMinutes)
        assertNull(stored.secondValue.escalationAfterMinutes)
        assertEquals(true, bundleVerdict(definition().copy(escalationAfterMinutes = 0)).valid)
        assertEquals(true, bundleVerdict(definition().copy(escalationAfterMinutes = null)).valid)
    }

    private fun parityCase(case: String): Pair<InformationRequestClockPolicyDefinition, Boolean> = when (case) {
        "positive-escalation" -> definition() to true
        "disabled-escalation" -> definition().copy(escalationAfterMinutes = null) to true
        "zero-escalation" -> definition().copy(escalationAfterMinutes = 0) to true
        "negative-escalation" -> definition().copy(escalationAfterMinutes = -1) to false
        "overlapping-periods" -> definition(periods = listOf(monday(540, 780), monday(720, 1020))) to false
        "overlapping-periods-out-of-order" -> definition(periods = listOf(monday(720, 1020), monday(540, 780))) to false
        "periods-sharing-a-start" -> definition(periods = listOf(monday(540, 600), monday(540, 700))) to false
        "adjacent-periods" -> definition(periods = listOf(monday(540, 780), monday(780, 1020))) to true
        "midnight-ending-period" -> definition(periods = listOf(monday(1380, 1440))) to true
        "inverted-period" -> definition(periods = listOf(monday(600, 540))) to false
        "period-past-midnight" -> definition(periods = listOf(monday(1020, 1441))) to false
        "period-starting-at-midnight" -> definition(periods = listOf(monday(1440, 1440))) to false
        "distinct-reminders" -> definition(reminders = listOf(480, 60)) to true
        "duplicate-reminders" -> definition(reminders = listOf(60, 60)) to false
        "zero-reminder" -> definition(reminders = listOf(0)) to false
        "urgent-as-long-as-standard" -> definition(urgent = 2400) to true
        "urgent-longer-than-standard" -> definition(urgent = 3000) to false
        "zero-standard" -> definition().copy(standardDurationMinutes = 0) to false
        "unknown-time-zone" -> definition(timezone = "Not/AZone") to false
        "padded-time-zone" -> definition(timezone = " Europe/Berlin ") to true
        "business-without-periods" -> definition(periods = emptyList()) to false
        "calendar-without-periods" -> definition(periods = emptyList()).copy(clockType = InformationRequestClockType.CALENDAR) to true
        "calendar-with-periods" -> definition().copy(clockType = InformationRequestClockType.CALENDAR) to false
        else -> error("Unknown parity case $case")
    }

    private fun authoringAccepts(case: String, definition: InformationRequestClockPolicyDefinition): Boolean =
        try {
            authoring.define(DefineInformationRequestClockPolicyCommand("window-$case", "Window", definition, case))
            true
        } catch (_: InformationRequestCommandRequestException) {
            false
        }

    private fun bundleVerdict(definition: InformationRequestClockPolicyDefinition): InformationRequestConfigurationBundleValidationDto {
        val bundle = InformationRequestConfigurationBundle(
            formatVersion = 1,
            bundleKey = "clock-parity",
            bundleVersion = 1,
            displayName = "Clock parity",
            clockPolicies = listOf(
                BundleClockPolicy(
                    policyKey = "response-window",
                    clockType = definition.clockType,
                    businessTimezone = definition.businessTimezone,
                    standardDurationMinutes = definition.standardDurationMinutes,
                    urgentDurationMinutes = definition.urgentDurationMinutes,
                    escalationAfterMinutes = definition.escalationAfterMinutes,
                    dueEffect = definition.dueEffect,
                    businessPeriods = definition.workingPeriods.map {
                        BundleClockPeriod(
                            it.dayOfWeek.value,
                            it.startMinute,
                            it.endMinute
                        )
                    },
                    holidays = definition.holidays.map { it.toString() },
                    reminderOffsetsMinutes = definition.reminderMinutesBeforeDue,
                ),
            ),
        )
        val response = bundles.validate(Json.encodeToString(InformationRequestConfigurationBundle.serializer(), bundle))
        assertEquals(200, response.status)
        return response.entity as InformationRequestConfigurationBundleValidationDto
    }

    private fun definition(
        timezone: String = "Africa/Johannesburg",
        periods: List<InformationRequestWorkingPeriod> = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ).map { InformationRequestWorkingPeriod(it, 540, 1020) },
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

    private fun monday(start: Int, end: Int) = InformationRequestWorkingPeriod(DayOfWeek.MONDAY, start, end)
}
