package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.clock.*
import com.docuhyphen.app.api.repository.informationrequest.clock.*
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.communication.CommunicationResolver
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditTargets
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.hibernate.exception.ConstraintViolationException
import java.sql.Timestamp
import java.time.Clock
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
    private val actorLabels: InformationRequestAuditActorLabelResolver,
    private val communications: CommunicationResolver,
    private val clock: Clock,
    private val commandReceiptService: CommandReceiptService,
)
{
    @Transactional
    fun define(command: DefineInformationRequestClockPolicyCommand): InformationRequestClockPolicyView
    {
        val owner = ownerAccess.currentOwner()
        val access = ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)
        val principal = access.principal
        val key = command.policyKey.trim().lowercase()
        InformationRequestClockInputLimits.requireLength(key, 128, "A policy key")
        InformationRequestClockInputLimits.requireLength(command.displayName.trim(), 255, "A policy name")
        if (!KEY.matches(key)) throw InformationRequestCommandRequestException("A clock policy key uses lowercase letters, digits, dots, dashes, or underscores")
        val name = command.displayName.trim()
            .ifBlank { throw InformationRequestCommandRequestException("A clock policy has a name") }
        validate(command.definition)
        requireVisibleCommunications(command.definition, owner)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_CLOCK_POLICY, owner.ownerId),
            operation = DEFINE_OPERATION,
            actor = CommandActorRef.principal(principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(
                DEFINE_OPERATION,
                owner.ownerType,
                owner.ownerId,
                key,
                name,
                fingerprintFieldsOf(command.definition),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                if (policyRepository.findByKey(owner.ownerType, owner.ownerId, key) != null) throw keyTaken()
                val policy = insertPolicy(
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
                publish(policy, 1, command.definition, InformationRequestAuditActor.of(access), owner)
                CommandMutationResult(view(policy), policyResult(policy, 1))
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> view(requirePolicy(decision.result.resourceId))
        }
    }

    @Transactional
    fun publishVersion(command: PublishInformationRequestClockPolicyVersionCommand): InformationRequestClockPolicyView
    {
        val policy = requirePolicy(command.policyId)
        val owner = ownerOf(policy)
        val access = ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)
        val principal = access.principal
        validate(command.definition)
        requireVisibleCommunications(command.definition, owner)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_CLOCK_POLICY, policy.id),
            operation = PUBLISH_OPERATION,
            actor = CommandActorRef.principal(principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(
                PUBLISH_OPERATION,
                policy.id,
                fingerprintFieldsOf(command.definition),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val locked = policyRepository.findByIdForUpdate(policy.id)
                    ?: throw InformationRequestLifecycleException(
                        InformationRequestErrorCatalog.NOT_FOUND,
                        "Clock policy not found"
                    )
                val number = versionRepository.nextVersionNumber(locked.id)
                publish(locked, number, command.definition, InformationRequestAuditActor.of(access), owner)
                CommandMutationResult(view(locked), policyResult(locked, number))
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> view(policy)
        }
    }

    fun list(): List<InformationRequestClockPolicyView>
    {
        val owner = ownerAccess.currentOwner()
        ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_TEMPLATE_VIEW)
        return policyRepository.findForOwner(owner.ownerType, owner.ownerId).map(::view)
    }

    fun get(policyId: UUID): InformationRequestClockPolicyView
    {
        val policy = requirePolicy(policyId)
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
        actor: InformationRequestAuditActor,
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
                publishedByPrincipalKind = actor.principal.kind
                publishedByPrincipalId = actor.principal.id
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
        val attribution = actorLabels.attribute(actor)
        auditRecorder.record(
            AuditEventDraft(
                owner = owner.auditOwner(),
                eventTypeKey = AuditEventType.INFORMATION_REQUEST_CLOCK_POLICY_PUBLISH.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                targetType = InformationRequestAuditTargets.CLOCK_POLICY,
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
        if (definition.holidays.size > 3660) InformationRequestClockInputLimits.refuse("A clock policy states at most 3660 holidays")
        InformationRequestClockPolicyRules.problemsOf(InformationRequestClockPolicyRules.termsOf(definition))
            .firstOrNull()
            ?.let { throw InformationRequestCommandRequestException(it.message) }
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

    private fun requirePolicy(policyId: UUID): InformationRequestClockPolicy =
        policyRepository.findById(policyId)
            ?: throw io.quarkus.security.ForbiddenException("Access denied to this owner's Information Request configuration")

    private fun insertPolicy(policy: InformationRequestClockPolicy): InformationRequestClockPolicy =
        try {
            policyRepository.insertNow(policy)
        } catch (exception: RuntimeException) {
            if (!takesExistingKey(exception)) throw exception
            throw keyTaken()
        }

    private fun takesExistingKey(exception: Throwable): Boolean =
        generateSequence(exception) { it.cause }
            .filterIsInstance<ConstraintViolationException>()
            .any { it.sqlState == UNIQUE_VIOLATION || it.constraintName.orEmpty().startsWith(KEY_CONSTRAINT_PREFIX) }

    private fun keyTaken() = InformationRequestLifecycleException(
        InformationRequestErrorCatalog.CLOCK_POLICY_KEY_TAKEN,
        "A clock policy with this key already exists"
    )

    private fun policyResult(policy: InformationRequestClockPolicy, versionNumber: Int) = CommandResultReference(
        resourceType = ResourceType.INFORMATION_REQUEST_CLOCK_POLICY,
        resourceId = policy.id,
        revision = versionNumber.toLong(),
    )

    private fun ownerOf(policy: InformationRequestClockPolicy) = InformationRequestOwnerRef(
        policy.ownerType,
        requireNotNull(policy.ownerOrganizationId ?: policy.ownerUserId) { "A clock policy has an owner" },
    )

    private fun fingerprintFieldsOf(definition: InformationRequestClockPolicyDefinition): List<Any?> = listOf(
        definition.clockType,
        definition.businessTimezone,
        definition.workingPeriods.map { listOf(it.dayOfWeek, it.startMinute, it.endMinute) },
        definition.holidays,
        definition.standardDurationMinutes,
        definition.urgentDurationMinutes,
        definition.reminderMinutesBeforeDue,
        definition.escalationAfterMinutes,
        definition.dueEffect,
        definition.reminderCommunicationId,
        definition.overdueCommunicationId,
    )

    private companion object
    {
        const val DEFINE_OPERATION = "define-information-request-clock-policy"
        const val PUBLISH_OPERATION = "publish-information-request-clock-policy-version"
        const val UNIQUE_VIOLATION = "23505"
        const val KEY_CONSTRAINT_PREFIX = "ux_information_request_clock_policy_"
        val KEY = Regex("^[a-z0-9][a-z0-9._-]*$")
    }
}
