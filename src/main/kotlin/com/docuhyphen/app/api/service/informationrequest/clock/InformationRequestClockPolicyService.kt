package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.clock.*
import com.docuhyphen.app.api.repository.informationrequest.clock.*
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.CommunicationResolver
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.*

@ApplicationScoped
class InformationRequestClockPolicyService @Inject constructor(
    private val ownerAccess: InformationRequestOwnerScopeAccess,
    private val policyRepository: InformationRequestClockPolicyRepository,
    private val versionRepository: InformationRequestClockPolicyVersionRepository,
    private val periodRepository: InformationRequestClockPolicyPeriodRepository,
    private val holidayRepository: InformationRequestClockPolicyHolidayRepository,
    private val reminderRepository: InformationRequestClockPolicyReminderRepository,
    private val auditRecorder: AuditRecorder,
    private val communications: CommunicationResolver,
    private val clock: Clock,
)
{
    @Transactional
    fun define(command: DefineInformationRequestClockPolicyCommand): InformationRequestClockPolicyView
    {
        val owner = ownerAccess.currentOwner()
        val principal = ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)
        val key = command.policyKey.trim().lowercase()
        if (!KEY.matches(key)) throw InformationRequestCommandRequestException("A clock policy key uses lowercase letters, digits, dots, dashes, or underscores")
        val name = command.displayName.trim()
            .ifBlank { throw InformationRequestCommandRequestException("A clock policy has a name") }
        validate(command.definition)
        requireVisibleCommunications(command.definition, owner)
        if (policyRepository.findByKey(owner.ownerType, owner.ownerId, key) != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN,
                "A clock policy with this key already exists"
            )
        }
        val policy = policyRepository.save(
            InformationRequestClockPolicy().apply {
                ownerType = owner.ownerType
                ownerOrganizationId = owner.organizationId
                ownerUserId = owner.userId
                policyKey = key
                displayName = name
                createdByPrincipalKind = principal.kind
                createdByPrincipalId = principal.id
                createdAt = Timestamp.from(clock.instant())
            },
        )
        publish(policy, 1, command.definition, principal, owner)
        return view(policy)
    }

    @Transactional
    fun publishVersion(command: PublishInformationRequestClockPolicyVersionCommand): InformationRequestClockPolicyView
    {
        val policy = policyRepository.findById(command.policyId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Clock policy not found"
            )
        val owner = ownerOf(policy)
        val principal = ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)
        validate(command.definition)
        requireVisibleCommunications(command.definition, owner)
        publish(policy, versionRepository.nextVersionNumber(policy.id), command.definition, principal, owner)
        return view(policy)
    }

    fun list(): List<InformationRequestClockPolicyView>
    {
        val owner = ownerAccess.currentOwner()
        ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_VIEW)
        return policyRepository.findForOwner(owner.ownerType, owner.ownerId).map(::view)
    }

    fun get(policyId: UUID): InformationRequestClockPolicyView
    {
        val policy = policyRepository.findById(policyId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Clock policy not found"
            )
        ownerAccess.requireAccess(ownerOf(policy), Action.INFORMATION_REQUEST_TEMPLATE_VIEW)
        return view(policy)
    }

    fun versionView(versionId: UUID): InformationRequestClockPolicyVersionView?
    {
        val version = versionRepository.findById(versionId) ?: return null
        return versionView(version)
    }

    fun ownerOfVersion(versionId: UUID): InformationRequestOwnerRef?
    {
        val version = versionRepository.findById(versionId) ?: return null
        return policyRepository.findById(version.policyId)?.let(::ownerOf)
    }

    private fun publish(
        policy: InformationRequestClockPolicy,
        number: Int,
        definition: InformationRequestClockPolicyDefinition,
        principal: PrincipalRef,
        owner: InformationRequestOwnerRef,
    )
    {
        val version = versionRepository.save(
            InformationRequestClockPolicyVersion().apply {
                policyId = policy.id
                versionNumber = number
                clockType = definition.clockType
                businessTimezone = definition.businessTimezone.trim()
                standardDurationMinutes = definition.standardDurationMinutes
                urgentDurationMinutes = definition.urgentDurationMinutes
                escalationAfterMinutes = definition.escalationAfterMinutes
                dueEffect = definition.dueEffect
                reminderCommunicationId = definition.reminderCommunicationId
                overdueCommunicationId = definition.overdueCommunicationId
                publishedByPrincipalKind = principal.kind
                publishedByPrincipalId = principal.id
                publishedAt = Timestamp.from(clock.instant())
            },
        )
        definition.workingPeriods.forEach { period ->
            periodRepository.save(
                InformationRequestClockPolicyPeriod().apply {
                    policyVersionId = version.id
                    dayOfWeek = period.dayOfWeek.value.toShort()
                    startMinute = period.startMinute
                    endMinute = period.endMinute
                },
            )
        }
        definition.holidays.distinct().forEach { date ->
            holidayRepository.save(InformationRequestClockPolicyHoliday().apply {
                policyVersionId = version.id; holidayDate = date
            })
        }
        definition.reminderMinutesBeforeDue.sortedDescending().forEachIndexed { index, minutes ->
            reminderRepository.save(
                InformationRequestClockPolicyReminder().apply {
                    policyVersionId = version.id
                    reminderOrdinal = index + 1
                    minutesBeforeDue = minutes
                },
            )
        }
        auditRecorder.record(
            AuditEventDraft(
                owner = owner.auditOwner(),
                eventTypeKey = AuditEventType.INFORMATION_REQUEST_CLOCK_POLICY_PUBLISH.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = principal.id,
                actorKind = AuditActorKind.forPrincipal(principal.kind),
                targetType = "INFORMATION_REQUEST_CLOCK_POLICY",
                targetId = policy.id.toString(),
                targetLabel = policy.displayName,
                payload = mapOf(
                    "policyKey" to policy.policyKey,
                    "versionNumber" to number.toString(),
                    "clockType" to definition.clockType.name,
                    "dueEffect" to definition.dueEffect.name,
                ),
                idempotencyKey = "information_request.clock_policy|${version.id}",
            ),
        )
    }

    private fun validate(definition: InformationRequestClockPolicyDefinition)
    {
        try
        {
            ZoneId.of(definition.businessTimezone.trim())
        }
        catch (_: DateTimeException)
        {
            throw InformationRequestCommandRequestException("A clock policy names a valid business timezone")
        }
        if (definition.urgentDurationMinutes <= 0 || definition.standardDurationMinutes < definition.urgentDurationMinutes)
        {
            throw InformationRequestCommandRequestException("A clock policy's urgent duration is positive and no longer than its standard duration")
        }
        if (definition.clockType == InformationRequestClockType.BUSINESS && definition.workingPeriods.isEmpty())
        {
            throw InformationRequestCommandRequestException("A business clock policy states at least one working period")
        }
        definition.workingPeriods.forEach { period ->
            if (period.startMinute < 0 || period.endMinute > MINUTES_PER_DAY || period.startMinute >= period.endMinute)
            {
                throw InformationRequestCommandRequestException("A working period starts before it ends within one day")
            }
        }
        definition.workingPeriods.groupBy { it.dayOfWeek }.values.forEach { day ->
            day.sortedBy { it.startMinute }.zipWithNext().forEach { (first, second) ->
                if (second.startMinute < first.endMinute)
                {
                    throw InformationRequestCommandRequestException("Working periods of one day do not overlap")
                }
            }
        }
        if (definition.reminderMinutesBeforeDue.any { it <= 0 } ||
            definition.reminderMinutesBeforeDue.distinct().size != definition.reminderMinutesBeforeDue.size)
        {
            throw InformationRequestCommandRequestException("Reminder points are distinct positive minutes before the due time")
        }
        if ((definition.escalationAfterMinutes ?: 0) < 0)
        {
            throw InformationRequestCommandRequestException("An escalation point is zero or more minutes after the due time")
        }
    }

    private fun requireVisibleCommunications(
        definition: InformationRequestClockPolicyDefinition,
        owner: InformationRequestOwnerRef
    )
    {
        listOfNotNull(definition.reminderCommunicationId, definition.overdueCommunicationId).forEach { id ->
            val source = communications.sourceOf(id)
            val visible = source != null && when (source.scope)
            {
                CommunicationScope.PLATFORM -> true
                CommunicationScope.ORG -> source.organizationId == owner.organizationId
                CommunicationScope.PERSONAL -> source.createdByAppUserId == owner.userId
            }
            if (!visible)
            {
                throw InformationRequestCommandRequestException("A clock policy names an active communication its owner can use")
            }
        }
    }

    private fun view(policy: InformationRequestClockPolicy): InformationRequestClockPolicyView =
        InformationRequestClockPolicyView(policy, versionRepository.findForPolicy(policy.id).map(::versionView))

    private fun versionView(version: InformationRequestClockPolicyVersion): InformationRequestClockPolicyVersionView
    {
        val holidays = holidayRepository.findForVersion(version.id).map { it.holidayDate }
        return InformationRequestClockPolicyVersionView(
            version = version,
            calendar = InformationRequestClockCalendar(
                clockType = version.clockType,
                zone = ZoneId.of(version.businessTimezone),
                periods = periodRepository.findForVersion(version.id).map {
                    InformationRequestWorkingPeriod(DayOfWeek.of(it.dayOfWeek.toInt()), it.startMinute, it.endMinute)
                },
                holidays = holidays.toSet(),
            ),
            holidays = holidays,
            reminderMinutesBeforeDue = reminderRepository.findForVersion(version.id).map { it.minutesBeforeDue },
        )
    }

    private fun ownerOf(policy: InformationRequestClockPolicy) = InformationRequestOwnerRef(
        policy.ownerType,
        requireNotNull(policy.ownerOrganizationId ?: policy.ownerUserId) { "A clock policy has an owner" },
    )

    private companion object
    {
        const val MINUTES_PER_DAY = 1440
        val KEY = Regex("^[a-z0-9][a-z0-9._-]*$")
    }
}
