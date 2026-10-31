package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.exception.WorkflowStartRefusedException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.workflow.*
import com.docuhyphen.app.api.repository.workflow.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowTriggerEventRepository
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.AppNotificationService
import com.docuhyphen.app.api.service.communication.CommunicationResolver
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.MarkdownRenderer
import com.docuhyphen.app.api.service.communication.templates.EmailTemplateRenderer
import com.docuhyphen.app.api.service.notification.*
import com.docuhyphen.app.api.service.variable.VariableResolutionContext
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import jakarta.transaction.Transactional
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class DefaultWorkflowEngineService : WorkflowEngineService
{
    private val logger = LoggerFactory.getLogger(DefaultWorkflowEngineService::class.java)

    @Inject
    private lateinit var definitionRepository: WorkflowDefinitionRepository

    @Inject
    private lateinit var instanceRepository: WorkflowInstanceRepository

    @Inject
    private lateinit var stepRepository: WorkflowStepInstanceRepository

    @Inject
    private lateinit var transitionRepository: com.docuhyphen.app.api.repository.workflow.WorkflowStepTransitionRepository

    @Inject
    private lateinit var assigneeRepository: com.docuhyphen.app.api.repository.workflow.WorkflowStepAssigneeRepository

    @Inject
    private lateinit var decisionRepository: com.docuhyphen.app.api.repository.workflow.WorkflowStepDecisionRepository

    @Inject
    private lateinit var assigneeResolver: WorkflowAssigneeResolver

    @Inject
    @field:TransactionalEventSink
    private lateinit var eventPublisher: DomainEventPublisher

    @Inject
    private lateinit var principalGroupMemberRepository: com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository

    @Inject
    private lateinit var principalGroupRepository: com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository

    @Inject
    private lateinit var exchangeRepository: com.docuhyphen.app.api.repository.exchange.ExchangeRepository

    @Inject
    private lateinit var appUserRepository: com.docuhyphen.app.api.repository.user.AppUserRepository

    @Inject
    private lateinit var appNotificationService: AppNotificationService

    @Inject
    private lateinit var emailService: EmailService

    @Inject
    private lateinit var communicationResolver: CommunicationResolver

    @Inject
    private lateinit var organizationRepository: com.docuhyphen.app.api.repository.organization.OrganizationRepository

    @Inject
    private lateinit var markdownRenderer: MarkdownRenderer

    @Inject
    private lateinit var emailTemplateRenderer: EmailTemplateRenderer

    @Inject
    private lateinit var applicabilityEvaluator: WorkflowApplicabilityEvaluator

    @Inject
    private lateinit var conditionPredicateService: ConditionPredicateService

    @Inject
    private lateinit var triggerEventRepository: WorkflowTriggerEventRepository

    @Inject
    private lateinit var transactionSynchronizationRegistry: TransactionSynchronizationRegistry

    @Inject
    private lateinit var inAppNotificationService: InAppNotificationService

    @Inject
    private lateinit var self: DefaultWorkflowEngineService

    @Inject
    private lateinit var subscriptionGuard: WorkflowSubscriptionGuard

    @ConfigProperty(name = "app.url", defaultValue = "https://app.docuhyphen.com")
    private lateinit var appUrl: String

    @ConfigProperty(name = "app.name", defaultValue = "DocuHyphen")
    private lateinit var appName: String

    @Inject
    private lateinit var actionHandlerBeans: Instance<WorkflowActionHandler>

    private val json = WorkflowSpecJson.instance

    private lateinit var actionHandlers: Map<String, WorkflowActionHandler>

    @PostConstruct
    fun buildActionHandlerRegistry()
    {
        actionHandlers = actionHandlerBeans.associate { it.key() to it }
        logger.info("Registered {} workflow action handler(s): {}", actionHandlers.size, actionHandlers.keys)
    }

    @Transactional(dontRollbackOn = [SubscriptionDenialException::class, WorkflowStartRefusedException::class])
    override fun trigger(request: TriggerRequest): TriggerResult?
    {
        val definitions = definitionsFor(request)
        if (definitions.isEmpty())
        {
            logger.debug(
                "No active workflow definition for trigger={} org={}",
                request.triggerEvent,
                request.organizationId
            )
            return null
        }

        definitions.forEach(::requireStartable)
        definitions.forEach { definition ->
            try {
                subscriptionGuard.requireInstanceStart(definition, request.organizationId)
            } catch (unresolved: IllegalArgumentException) {
                throw WorkflowStartRefusedException(definition.id, WorkflowStartRefusedException.OWNER_UNRESOLVED)
            }
        }
        val enrichedRequest = enrichSubjectData(request)
        var firstResult: TriggerResult? = null
        for (definition in definitions)
        {
            val result = triggerOne(definition, enrichedRequest)
            if (firstResult == null) firstResult = result
        }
        return firstResult
    }

    private fun definitionsFor(request: TriggerRequest): List<WorkflowDefinition> {
        val personalOwner = request.ownerAppUserId
        return if (request.organizationId == null && personalOwner != null)
            definitionRepository.findAllActiveForPersonalTrigger(request.triggerEvent, personalOwner)
        else definitionRepository.findAllActiveForTrigger(request.triggerEvent, request.organizationId)
    }

    private fun requireStartable(definition: WorkflowDefinition) {
        val spec = try {
            WorkflowSpecJson.decode(definition.stepsJson)
        } catch (unreadable: IllegalArgumentException) {
            throw WorkflowStartRefusedException(definition.id, WorkflowStartRefusedException.SPEC_UNREADABLE)
        }
        val assignees = spec.steps.firstOrNull()?.assignees.orEmpty()
        if (assignees.any { !namesResolvablePrincipal(it) }) {
            throw WorkflowStartRefusedException(definition.id, WorkflowStartRefusedException.ASSIGNEE_INVALID)
        }
    }

    private fun namesResolvablePrincipal(assignee: AssigneeSpec): Boolean = when (assignee) {
        is AssigneeSpec.Principal -> isIdentifier(assignee.principalId)
        is AssigneeSpec.GroupRoleAssignees -> isSubjectReference(assignee.groupIdRef) || isIdentifier(assignee.groupIdRef)
        is AssigneeSpec.OrganizationRoleAssignees ->
            isSubjectReference(assignee.organizationIdRef) || isIdentifier(assignee.organizationIdRef)

        is AssigneeSpec.AppRoleAssignees -> true
    }

    private fun isSubjectReference(reference: String): Boolean = reference.startsWith(SUBJECT_REFERENCE_PREFIX)

    private fun isIdentifier(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess

    private fun enrichSubjectData(request: TriggerRequest): TriggerRequest
    {
        if (request.subjectResourceType != ResourceType.EXCHANGE.name || request.subjectResourceId == null)
        {
            return request
        }
        if (!::exchangeRepository.isInitialized) return request

        val exchange = runCatching { exchangeRepository.findById(request.subjectResourceId) }.getOrNull()
            ?: return request
        val enriched = request.subjectData.toMutableMap()

        fun putIfMissing(key: String, value: String?)
        {
            val normalized = value?.trim()?.takeIf { it.isNotBlank() } ?: return
            if (enriched[key].isNullOrBlank()) enriched[key] = normalized
        }

        putIfMissing("exchangeName", exchange.name)
        putIfMissing("orgId", exchange.ownerOrganizationId?.toString())
        exchange.initiator?.let { initiator ->
            putIfMissing("initiatorId", initiator.id.toString())
            val initiatorName = initiator.person?.let { person ->
                listOfNotNull(person.firstName, person.lastName)
                    .joinToString(" ")
                    .trim()
                    .takeIf { it.isNotBlank() }
            } ?: runCatching { initiator.email }.getOrNull()
            putIfMissing("initiatorName", initiatorName)
        }

        return if (enriched == request.subjectData) request else request.copy(subjectData = enriched)
    }

    private fun triggerOne(
        definition: com.docuhyphen.app.api.model.entity.WorkflowDefinition,
        request: TriggerRequest
    ): TriggerResult?
    {
        val spec = WorkflowSpecJson.decode(definition.stepsJson)
        if (spec.steps.isEmpty())
        {
            logger.warn("Workflow definition {} has no steps; skipping", definition.id)
            return null
        }

        if (!applicabilityEvaluator.isApplicable(
                request.subjectResourceType,
                request.subjectResourceId,
                request.organizationId,
                spec.applicability,
                request.subjectData,
            )
        )
        {
            logger.debug(
                "Workflow definition {} skipped: applicability conditions not met for subject {}",
                definition.id, request.subjectResourceId,
            )
            return null
        }

        val instance = WorkflowInstance().apply {
            definitionId = definition.id
            definitionVersion = definition.version
            subjectResourceType = request.subjectResourceType
            subjectResourceId = request.subjectResourceId
            organizationId = request.organizationId
            status = WorkflowInstanceStatus.RUNNING
            currentStepIndex = 0
            subjectDataJson = encodeStringMap(request.subjectData)
            definitionSnapshotJson = definition.stepsJson
            triggerEventSnapshot = definition.triggerEvent
            initiatedByAppUserId = request.initiatedByAppUserId
        }
        instanceRepository.save(instance)

        val firstSpec = spec.steps[0]
        val resolved = assigneeResolver.resolveAll(firstSpec.assignees, instance.subjectDataJson)
        val stepInstance = createStepInstance(instance, 0, firstSpec)
        stepRepository.save(stepInstance)
        replaceAssignees(stepInstance.id, resolved)

        recordStartTransition(instance, stepInstance.stepIndex)

        if (quorumUnsatisfiable(firstSpec, resolved.size))
        {
            failInstance(
                instance,
                stepInstance,
                code = FAILURE_QUORUM_UNSATISFIABLE,
                detail = "trigger: N_OF_M quorum exceeds the ${resolved.size} resolved assignee(s)",
            )
            instanceRepository.update(instance)
            return TriggerResult(
                instanceId = instance.id,
                definitionId = definition.id,
                firstStepInstanceId = stepInstance.id,
                firstStepAssignees = resolved,
            )
        }

        val autoEvents = activateStep(instance, stepInstance, firstSpec, resolved)
        if (stepInstance.status != WorkflowStepStatus.PENDING)
        {
            stepRepository.update(stepInstance)
            autoEvents.forEach { publishOutcomeEvent(instance, it) }
        }
        instanceRepository.update(instance)

        return TriggerResult(
            instanceId = instance.id,
            definitionId = definition.id,
            firstStepInstanceId = stepInstance.id,
            firstStepAssignees = resolved,
        )
    }

    @Transactional
    override fun recordDecision(
        stepInstanceId: UUID,
        decider: PrincipalRef,
        decision: Decision,
        reason: String?,
    ): DecisionResult
    {
        val parentInstanceId = stepRepository.findInstanceIdById(stepInstanceId)
            ?: throw IllegalArgumentException("Step instance $stepInstanceId not found")
        val instance = instanceRepository.findByIdForUpdate(parentInstanceId)
            ?: throw IllegalStateException("Workflow instance $parentInstanceId missing")
        val step = stepRepository.findByIdForUpdate(stepInstanceId)
            ?: throw IllegalArgumentException("Step instance $stepInstanceId not found")

        val assignees = assigneeRepository.findAllByStepInstanceId(step.id)
        if (assignees.none { it.principalKind == decider.kind && it.principalId == decider.id })
        {
            throw IllegalStateException("Decider ${decider.kind}/${decider.id} is not an assignee of step $stepInstanceId")
        }
        markWorkflowAssignmentNotificationRead(decider, step.id)

        if (step.status != WorkflowStepStatus.PENDING || !instance.status.isActive)
        {
            return DecisionResult(
                instanceId = instance.id,
                stepInstanceId = step.id,
                stepStatus = step.status,
                instanceStatus = instance.status,
                emittedEvents = emptyList(),
            )
        }

        val now = Timestamp.from(Instant.now())

        val priorDecision = decisionRepository.findByStepAndPrincipal(step.id, decider.kind, decider.id)
        if (priorDecision != null)
        {
            priorDecision.decision = decision.name
            priorDecision.reason = reason
            priorDecision.decidedAt = now
            decisionRepository.update(priorDecision)
        }
        else
        {
            decisionRepository.save(
                com.docuhyphen.app.api.model.entity.WorkflowStepDecision().apply {
                    this.stepInstanceId = step.id
                    this.principalKind = decider.kind
                    this.principalId = decider.id
                    this.decision = decision.name
                    this.reason = reason
                    this.decidedAt = now
                }
            )
        }
        val approvals = decisionRepository.findAllByStepInstanceId(step.id)
            .count { it.decision == Decision.APPROVE.name }

        val spec = WorkflowSpecJson.decodeStep(step.specSnapshotJson)
        val emitted = mutableListOf<String>()

        if (decision == Decision.REJECT)
        {
            step.status = WorkflowStepStatus.REJECTED
            step.completedAt = now
            instance.status = WorkflowInstanceStatus.REJECTED
            instance.completedAt = now
            recordTransition(instance, step, toStepIndex = null, WorkflowTransitionOutcome.REJECT)
            spec.onReject?.emit?.let { emitted += it }
            emitDefinitionTerminalEvent(instance, success = false)
        }
        else if (quorumMet(approvals, spec.quorum, assigneeTotal = assignees.size))
        {
            step.status = WorkflowStepStatus.APPROVED
            step.completedAt = now
            spec.onApprove?.emit?.let { emitted += it }
            advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step, WorkflowTransitionOutcome.APPROVE)
        }

        stepRepository.update(step)
        instanceRepository.update(instance)

        emitted.forEach { publishOutcomeEvent(instance, it) }

        return DecisionResult(
            instanceId = instance.id,
            stepInstanceId = step.id,
            stepStatus = step.status,
            instanceStatus = instance.status,
            emittedEvents = emitted,
        )
    }

    @Transactional
    override fun escalateOverdue(now: Timestamp): Int
    {
        stepRepository.findAllPending().forEach { candidate ->
            val instance = instanceRepository.findByIdForUpdate(candidate.instanceId) ?: return@forEach
            if (!instance.status.isActive) return@forEach
            val step = stepRepository.findByIdForUpdate(candidate.id) ?: return@forEach
            if (step.status != WorkflowStepStatus.PENDING) return@forEach
            val spec = WorkflowSpecJson.decodeStep(step.specSnapshotJson)
            if (spec.addons.isNotEmpty())
            {
                processAddons(step, spec, now, instance)
            }
        }

        val overdue = stepRepository.findPendingDueBefore(now)
        if (overdue.isEmpty()) return 0

        var escalated = 0
        for (candidate in overdue)
        {
            val instance = instanceRepository.findByIdForUpdate(candidate.instanceId) ?: continue
            if (!instance.status.isActive) continue
            val step = stepRepository.findByIdForUpdate(candidate.id) ?: continue
            if (step.status != WorkflowStepStatus.PENDING) continue
            if (step.escalatedAt != null) continue

            val spec = WorkflowSpecJson.decodeStep(step.specSnapshotJson)
            val escalation = spec.escalation
            when (escalation?.afterSlaBreach)
            {
                EscalationAction.AUTO_REJECT ->
                {
                    step.status = WorkflowStepStatus.REJECTED
                    step.completedAt = now
                    instance.status = WorkflowInstanceStatus.REJECTED
                    instance.completedAt = now
                    recordTransition(instance, step, toStepIndex = null, WorkflowTransitionOutcome.REJECT)
                    spec.onReject?.emit?.let { publishOutcomeEvent(instance, it) }
                    emitDefinitionTerminalEvent(instance, success = false)
                }

                EscalationAction.AUTO_APPROVE ->
                {
                    step.status = WorkflowStepStatus.APPROVED
                    step.completedAt = now
                    spec.onApprove?.emit?.let { publishOutcomeEvent(instance, it) }
                    advanceOrComplete(
                        instance,
                        spec.onApprove?.nextStep ?: "END",
                        now,
                        step,
                        WorkflowTransitionOutcome.APPROVE
                    )
                }

                EscalationAction.ESCALATE, null ->
                {
                    val targets = escalation?.escalateTo
                        ?.let { assigneeResolver.resolveAll(it, instance.subjectDataJson) }
                        ?: emptyList()
                    if (targets.isNotEmpty())
                    {
                        replaceAssignees(step.id, targets)
                        step.dueAt = spec.slaMinutes?.let { Timestamp.from(now.toInstant().plusSeconds(it * 60L)) }
                    }
                    instance.status = WorkflowInstanceStatus.ESCALATED
                    publishEscalated(instance, step, targets)
                }
            }
            step.escalatedAt = now
            stepRepository.update(step)
            instanceRepository.update(instance)
            escalated++
        }
        logger.info("Escalated {} overdue workflow steps", escalated)
        return escalated
    }

    @Transactional
    override fun cancel(instanceId: UUID, reason: String?)
    {
        val instance = instanceRepository.findByIdForUpdate(instanceId) ?: return
        if (!instance.status.isActive) return
        instance.status = WorkflowInstanceStatus.CANCELLED
        instance.completedAt = Timestamp.from(Instant.now())
        instanceRepository.update(instance)

        stepRepository.findByInstance(instanceId)
            .filter { it.status == WorkflowStepStatus.PENDING }
            .forEach { candidate ->
                val step = stepRepository.findByIdForUpdate(candidate.id) ?: return@forEach
                if (step.status != WorkflowStepStatus.PENDING) return@forEach
                step.status = WorkflowStepStatus.SKIPPED
                step.completedAt = instance.completedAt
                stepRepository.update(step)
            }
        if (reason != null)
        {
            logger.info("Workflow instance {} cancelled: {}", instanceId, reason)
        }
    }

    @Transactional
    override fun cancelActiveForSubject(subjectResourceType: String, subjectResourceId: UUID, reason: String?): Int {
        val active = instanceRepository.findAllActiveForSubject(subjectResourceType, subjectResourceId)
        active.forEach { cancel(it.id, reason) }
        return active.size
    }

    override fun decisionsOf(instanceId: UUID): List<WorkflowDecisionRecord> =
        stepRepository.findByInstance(instanceId)
            .flatMap { step -> decisionRepository.findAllByStepInstanceId(step.id) }
            .mapNotNull { recorded ->
                Decision.entries.firstOrNull { it.name == recorded.decision }?.let { decision ->
                    WorkflowDecisionRecord(
                        principal = PrincipalRef(recorded.principalKind, recorded.principalId),
                        decision = decision,
                        decidedAt = recorded.decidedAt.toInstant(),
                    )
                }
            }
            .sortedBy { it.decidedAt }

    override fun listPendingForUser(appUserId: UUID): List<PendingWorkflowStepDto>
    {
        val userGroupIds: Set<UUID> = principalGroupMemberRepository
            .findGroupsForPrincipal(PrincipalKind.USER, appUserId)
            .map { it.principalGroupId }
            .toSet()

        return stepRepository.findPendingForAssignee(appUserId, userGroupIds).mapNotNull { step ->
            val alreadyVoted = decisionRepository
                .findByStepAndPrincipal(step.id, PrincipalKind.USER, appUserId) != null
            if (alreadyVoted) return@mapNotNull null

            val instance = instanceRepository.findById(step.instanceId) ?: return@mapNotNull null
            val subjectData = decodeSubjectData(instance.subjectDataJson)
            val exchangeId =
                if (instance.subjectResourceType == null || instance.subjectResourceType == ResourceType.EXCHANGE.name)
                    instance.subjectResourceId
                else
                    subjectData["exchangeId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val session = exchangeId?.let { exchangeRepository.findById(it) }
            val initiator = instance.initiatedByAppUserId?.let { appUserRepository.findById(it) }
            val groupName = subjectData["recipientGroupId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?.let { principalGroupRepository.findById(it)?.name }

            PendingWorkflowStepDto(
                stepInstanceId = step.id.toString(),
                workflowInstanceId = instance.id.toString(),
                stepType = step.stepType.name,
                exchangeId = exchangeId?.toString(),
                name = session?.name,
                requestedByEmail = initiator?.email,
                requestedByName = initiator?.person?.let { p ->
                    "${p.firstName.orEmpty()} ${p.lastName.orEmpty()}".trim().takeIf { it.isNotBlank() }
                },
                groupName = groupName,
                createdAtEpochMillis = step.createdAt.time,
            )
        }
    }

    private fun activateStep(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        spec: WorkflowStepSpec,
        resolved: List<PrincipalRef>,
    ): List<String>
    {
        return when (spec.type)
        {
            WorkflowStepType.APPROVAL ->
            {
                publishStepAssigned(instance, step, resolved)
                emptyList()
            }

            WorkflowStepType.NOTIFICATION -> executeNotificationStep(instance, step, spec)
            WorkflowStepType.CONDITION -> executeConditionStep(instance, step, spec)
            WorkflowStepType.ACTION -> executeActionStep(instance, step, spec)
            WorkflowStepType.WAIT_FOR_COUNTERPARTY_CLEARANCE -> executeWaitForCounterpartyStep(instance, step, spec)
        }
    }

    private fun executeNotificationStep(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        spec: WorkflowStepSpec,
    ): List<String>
    {
        val now = Timestamp.from(Instant.now())
        val assignees = assigneeRepository.findAllByStepInstanceId(step.id)
        val subjectData = decodeSubjectData(instance.subjectDataJson)

        var renderedSubject: String? = null
        var renderedBody: String? = null

        if (spec.communicationId != null)
        {
            val contextUser = instance.initiatedByAppUserId?.let { appUserRepository.findById(it) }
            val contextOrg = instance.organizationId?.let { organizationRepository.findById(it) }
            if (contextUser != null)
            {
                val ctx = VariableResolutionContext(
                    user = contextUser,
                    organization = contextOrg,
                    timestamp = now.toInstant(),
                )
                val rendered = runCatching {
                    communicationResolver.resolve(spec.communicationId, subjectData, ctx)
                }.getOrNull()
                renderedSubject = rendered?.subject
                renderedBody = rendered?.body
            }
        }

        val subject = renderedSubject ?: "Workflow Notification"
        val plainBody = renderedBody ?: "You have a notification from a workflow on DocuHyphen."

        for (a in assignees)
        {
            if (a.principalKind == PrincipalKind.USER)
            {
                val uid = a.principalId
                val user = appUserRepository.findById(uid)
                if (user != null)
                {
                    appNotificationService.sendNotification(uid.toString(), subject, plainBody)
                    try
                    {
                        if (renderedBody != null)
                        {
                            val html = markdownRenderer.toHtml(renderedBody)
                            val wrappedHtml = runCatching {
                                emailTemplateRenderer.render(
                                    "communication-wrapper.ftl",
                                    mapOf(
                                        "appName" to appName,
                                        "appUrl" to appUrl,
                                        "htmlBody" to html,
                                        "emailTitle" to subject,
                                    )
                                )
                            }.getOrElse { html }
                            emailService.sendEmail(user.email, subject, wrappedHtml, useHtml = true)
                        }
                        else
                        {
                            emailService.sendEmail(user.email, subject, plainBody)
                        }
                    }
                    catch (e: Exception)
                    {
                        logger.warn("Email notification failed for {}: {}", user.email, e.message)
                    }
                }
            }
        }

        step.status = WorkflowStepStatus.COMPLETED
        step.completedAt = now

        val emitted = mutableListOf<String>()
        spec.onApprove?.emit?.let { emitted += it }
        advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step, WorkflowTransitionOutcome.APPROVE)
        return emitted
    }

    private fun executeConditionStep(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        spec: WorkflowStepSpec,
    ): List<String>
    {
        val now = Timestamp.from(Instant.now())
        val subjectData = decodeSubjectData(instance.subjectDataJson)
        val fields = instance.triggerEventSnapshot
            ?.let { triggerEvent -> workflowSubjectFields(triggerEvent) }
            .orEmpty()
        val evaluation = conditionPredicateService.evaluate(spec.predicateExpression, fields, subjectData)
        val result = (evaluation as? PredicateResult.Valid)?.matches == true
        logger.debug(
            "CONDITION step {} evaluated to {} with result category {}",
            step.id,
            result,
            evaluation::class.simpleName
        )

        step.status = WorkflowStepStatus.COMPLETED
        step.completedAt = now

        val outcome = if (result) spec.onTrue else spec.onFalse
        val emitted = mutableListOf<String>()
        outcome?.emit?.let { emitted += it }
        advanceOrComplete(
            instance,
            outcome?.nextStep ?: "END",
            now,
            step,
            if (result) WorkflowTransitionOutcome.TRUE else WorkflowTransitionOutcome.FALSE,
        )
        return emitted
    }

    private fun executeActionStep(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        spec: WorkflowStepSpec,
    ): List<String>
    {
        val now = Timestamp.from(Instant.now())
        val handlerKey = spec.actionHandlerKey

        if (handlerKey == null)
        {
            logger.warn("ACTION step {} has no actionHandlerKey; auto-completing as success", step.id)
            step.status = WorkflowStepStatus.COMPLETED
            step.completedAt = now
            val emitted = mutableListOf<String>()
            spec.onApprove?.emit?.let { emitted += it }
            advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step, WorkflowTransitionOutcome.APPROVE)
            return emitted
        }

        val handler = actionHandlers[handlerKey]
        if (handler == null)
        {
            logger.error("No WorkflowActionHandler for key '{}'; rejecting step {}", handlerKey, step.id)
            step.status = WorkflowStepStatus.REJECTED
            step.completedAt = now
            instance.status = WorkflowInstanceStatus.REJECTED
            instance.completedAt = now
            recordTransition(instance, step, toStepIndex = null, WorkflowTransitionOutcome.REJECT)
            val emitted = mutableListOf<String>()
            spec.onReject?.emit?.let { emitted += it }
            emitDefinitionTerminalEvent(instance, success = false)
            return emitted
        }

        val result = runCatching { handler.execute(instance, step) }.getOrElse { e ->
            logger.error("WorkflowActionHandler '{}' threw for step {}: {}", handlerKey, step.id, e.message)
            ActionResult(success = false, reason = e.message)
        }

        val emitted = mutableListOf<String>()
        if (result.success)
        {
            step.status = WorkflowStepStatus.COMPLETED
            step.completedAt = now
            spec.onApprove?.emit?.let { emitted += it }
            advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step, WorkflowTransitionOutcome.APPROVE)
        }
        else
        {
            logger.warn("ACTION step {} failed (handler='{}'): {}", step.id, handlerKey, result.reason)
            step.status = WorkflowStepStatus.REJECTED
            step.completedAt = now
            instance.status = WorkflowInstanceStatus.REJECTED
            instance.completedAt = now
            recordTransition(instance, step, toStepIndex = null, WorkflowTransitionOutcome.REJECT)
            spec.onReject?.emit?.let { emitted += it }
            emitDefinitionTerminalEvent(instance, success = false)
        }
        return emitted
    }

    private fun executeWaitForCounterpartyStep(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        spec: WorkflowStepSpec,
    ): List<String>
    {
        val rt = instance.subjectResourceType ?: return emptyList()
        val rid = instance.subjectResourceId ?: return emptyList()
        val myOrgId = instance.organizationId

        val counterpartyRunning = if (myOrgId != null)
            hasBlockingCounterparty(rt, rid, myOrgId)
        else
            false

        if (counterpartyRunning)
        {
            step.status = WorkflowStepStatus.AWAITING_COUNTERPARTY
            logger.info("Step {} parked AWAITING_COUNTERPARTY for Exchange {}", step.id, rid)
            return emptyList()
        }

        val now = java.sql.Timestamp.from(java.time.Instant.now())
        step.status = WorkflowStepStatus.COMPLETED
        step.completedAt = now
        val emitted = mutableListOf<String>()
        spec.onApprove?.emit?.let { emitted += it }
        advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step, WorkflowTransitionOutcome.DEFAULT)
        return emitted
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    open fun unblockWaitingCounterpartySteps(resourceType: String, resourceId: java.util.UUID)
    {
        val waiting = stepRepository.findAwaitingCounterpartyForSubject(resourceType, resourceId)
        if (waiting.isEmpty()) return

        for (candidate in waiting)
        {
            val parentInstance = instanceRepository.findByIdForUpdate(candidate.instanceId) ?: continue
            if (!parentInstance.status.isActive) continue
            val step = stepRepository.findByIdForUpdate(candidate.id) ?: continue
            if (step.status != WorkflowStepStatus.AWAITING_COUNTERPARTY) continue

            val myOrgId = parentInstance.organizationId
            val counterpartyRunning = if (myOrgId != null)
                hasBlockingCounterparty(resourceType, resourceId, myOrgId)
            else
                false

            if (!counterpartyRunning)
            {
                val now = java.sql.Timestamp.from(java.time.Instant.now())
                val spec = WorkflowSpecJson.decodeStep(step.specSnapshotJson)
                step.status = WorkflowStepStatus.COMPLETED
                step.completedAt = now
                stepRepository.update(step)

                val emitted = mutableListOf<String>()
                spec.onApprove?.emit?.let { emitted += it }
                advanceOrComplete(
                    parentInstance,
                    spec.onApprove?.nextStep ?: "END",
                    now,
                    step,
                    WorkflowTransitionOutcome.DEFAULT
                )
                instanceRepository.update(parentInstance)
                emitted.forEach { publishOutcomeEvent(parentInstance, it) }
                logger.info("Unblocked AWAITING_COUNTERPARTY step {} for instance {}", step.id, parentInstance.id)
            }
        }
    }

    private fun hasBlockingCounterparty(resourceType: String, resourceId: UUID, organizationId: UUID): Boolean =
        instanceRepository.findForSubjectExcludingOrg(resourceType, resourceId, organizationId)
            .any { counterparty ->
                if (!counterparty.status.isActive) return@any false
                val current = stepRepository.findCurrent(counterparty.id, counterparty.currentStepIndex)
                current?.status != WorkflowStepStatus.AWAITING_COUNTERPARTY
            }

    private fun processAddons(
        step: WorkflowStepInstance,
        spec: WorkflowStepSpec,
        now: Timestamp,
        instance: WorkflowInstance,
    )
    {
        val state = decodeAddonsState(step.addonsStateJson).toMutableMap()
        var stateChanged = false
        val subjectFields = decodeSubjectData(instance.subjectDataJson)

        spec.addons.forEachIndexed { idx, addon ->
            val key = idx.toString()
            val addonState = state[key]?.toMutableMap() ?: mutableMapOf()

            when (addon)
            {
                is StepAddonSpec.ReminderBeforeDue ->
                {
                    if (addonState["fired"] == "true") return@forEachIndexed
                    val dueAt = step.dueAt ?: return@forEachIndexed
                    val minutesRemaining = (dueAt.time - now.time) / 60_000L
                    if (minutesRemaining <= addon.minutesBeforeDue)
                    {
                        dispatchAddonReminder(addon.recipientRef, addon.communicationId, subjectFields)
                        addonState["fired"] = "true"
                        addonState["firedAt"] = now.time.toString()
                        addonState["fireCount"] = "1"
                        addonState["lastFiredAt"] = now.time.toString()
                        state[key] = addonState
                        stateChanged = true
                        logger.debug("REMINDER_BEFORE_DUE addon {} fired for step {}", idx, step.id)
                    }
                }

                is StepAddonSpec.ReminderIfNoDecision ->
                {
                    if (decisionRepository.countByStepInstanceId(step.id) > 0) return@forEachIndexed
                    val minutesPending = (now.time - step.createdAt.time) / 60_000L
                    if (minutesPending < addon.afterMinutes) return@forEachIndexed

                    val fireCount = addonState["fireCount"]?.toIntOrNull() ?: 0
                    val lastFiredAt = addonState["lastFiredAt"]?.toLongOrNull()

                    val shouldFire = when
                    {
                        fireCount == 0 -> true
                        addon.repeatEveryMinutes != null && lastFiredAt != null ->
                            (now.time - lastFiredAt) / 60_000L >= addon.repeatEveryMinutes

                        else -> false
                    }

                    if (shouldFire)
                    {
                        dispatchAddonReminder(addon.recipientRef, addon.communicationId, subjectFields)
                        addonState["fired"] = "true"
                        addonState["firedAt"] = addonState["firedAt"] ?: now.time.toString()
                        addonState["fireCount"] = (fireCount + 1).toString()
                        addonState["lastFiredAt"] = now.time.toString()
                        state[key] = addonState
                        stateChanged = true
                        logger.debug(
                            "REMINDER_IF_NO_DECISION addon {} fired (count={}) for step {}",
                            idx,
                            fireCount + 1,
                            step.id
                        )
                    }
                }
            }
        }

        if (stateChanged)
        {
            step.addonsStateJson = encodeAddonsState(state)
            stepRepository.update(step)
        }
    }

    private fun dispatchAddonReminder(
        recipientRef: AssigneeSpec,
        communicationId: String?,
        subjectFields: Map<String, String>,
    )
    {
        val resolved = assigneeResolver.resolveOne(recipientRef, subjectFields)
        val subject = "Reminder: Action Required"
        val body = communicationId ?: "A reminder: your decision is still pending on a workflow step."
        for (p in resolved)
        {
            if (p.kind == PrincipalKind.USER)
            {
                appNotificationService.sendNotification(p.id.toString(), subject, body)
                val user = appUserRepository.findById(p.id)
                if (user != null)
                {
                    try
                    {
                        emailService.sendEmail(user.email, subject, body)
                    }
                    catch (e: Exception)
                    {
                        logger.warn("Reminder email failed for {}: {}", user.email, e.message)
                    }
                }
            }
        }
    }

    private fun advanceOrComplete(
        instance: WorkflowInstance,
        nextRef: String,
        now: Timestamp,
        fromStep: WorkflowStepInstance,
        outcome: WorkflowTransitionOutcome,
    )
    {
        if (nextRef.equals("END", ignoreCase = true))
        {
            recordTransition(instance, fromStep, toStepIndex = null, outcome)
            instance.status = WorkflowInstanceStatus.COMPLETED
            instance.completedAt = now
            emitDefinitionTerminalEvent(instance, success = true)
        }
        else
        {
            val nextIndex = nextRef.toIntOrNull()
            if (nextIndex == null)
            {
                failInstance(
                    instance,
                    fromStep,
                    code = FAILURE_ROUTE_INVALID,
                    detail = "advanceOrComplete: route target '$nextRef' is neither END nor a step index",
                )
            }
            else
            {
                advanceToStep(instance, nextIndex, fromStep, outcome)
            }
        }
    }

    private fun advanceToStep(
        instance: WorkflowInstance,
        nextIndex: Int,
        fromStep: WorkflowStepInstance,
        outcome: WorkflowTransitionOutcome,
    )
    {
        val spec = instanceExecutionSpec(instance)
        if (spec == null)
        {
            failInstance(
                instance,
                fromStep,
                code = snapshotFailureCode(instance),
                detail = "advanceToStep(index=$nextIndex): execution snapshot is ${snapshotFailureCode(instance)}",
            )
            return
        }
        if (nextIndex !in spec.steps.indices)
        {
            failInstance(
                instance,
                fromStep,
                code = FAILURE_ROUTE_INVALID,
                detail = "advanceToStep: target index $nextIndex is outside 0..${spec.steps.size - 1}",
            )
            return
        }
        recordTransition(instance, fromStep, toStepIndex = nextIndex, outcome)
        instance.currentStepIndex = nextIndex
        val nextSpec = spec.steps[nextIndex]
        val resolved = assigneeResolver.resolveAll(nextSpec.assignees, instance.subjectDataJson)
        val newStep = createStepInstance(instance, nextIndex, nextSpec)
        stepRepository.save(newStep)
        replaceAssignees(newStep.id, resolved)

        if (quorumUnsatisfiable(nextSpec, resolved.size))
        {
            failInstance(
                instance,
                newStep,
                code = FAILURE_QUORUM_UNSATISFIABLE,
                detail = "advanceToStep: N_OF_M quorum exceeds the ${resolved.size} resolved assignee(s)",
            )
            return
        }

        val autoEvents = activateStep(instance, newStep, nextSpec, resolved)
        if (newStep.status != WorkflowStepStatus.PENDING)
        {
            stepRepository.update(newStep)
            autoEvents.forEach { publishOutcomeEvent(instance, it) }
        }
    }

    private fun instanceExecutionSpec(instance: WorkflowInstance): WorkflowSpec?
    {
        val snapshot = instance.definitionSnapshotJson
        if (snapshot.isNullOrBlank()) return null
        return runCatching { WorkflowSpecJson.decode(snapshot) }.getOrNull()
    }

    private fun snapshotFailureCode(instance: WorkflowInstance): String =
        if (instance.definitionSnapshotJson.isNullOrBlank()) FAILURE_SNAPSHOT_MISSING else FAILURE_SNAPSHOT_CORRUPT

    private fun failInstance(
        instance: WorkflowInstance,
        fromStep: WorkflowStepInstance,
        code: String,
        detail: String,
    )
    {
        val now = Timestamp.from(Instant.now())
        recordTransition(instance, fromStep, toStepIndex = null, WorkflowTransitionOutcome.FAILED)
        instance.status = WorkflowInstanceStatus.FAILED
        instance.failureCode = code
        instance.failureDetail = detail
        instance.completedAt = now
        logger.error(
            "Workflow instance {} FAILED ({}) at step index {}: {}",
            instance.id, code, fromStep.stepIndex, detail,
        )
        publishFailed(instance, code)
    }

    private fun recordStartTransition(instance: WorkflowInstance, firstStepIndex: Int)
    {
        if (transitionRepository.existsStartByInstanceId(instance.id)) return
        transitionRepository.save(
            WorkflowStepTransition().apply {
                instanceId = instance.id
                fromStepInstanceId = null
                fromStepIndex = null
                toStepIndex = firstStepIndex
                outcome = WorkflowTransitionOutcome.DEFAULT
                recordedAt = Timestamp.from(Instant.now())
            }
        )
    }

    private fun recordTransition(
        instance: WorkflowInstance,
        fromStep: WorkflowStepInstance,
        toStepIndex: Int?,
        outcome: WorkflowTransitionOutcome,
    )
    {
        if (transitionRepository.existsByFromStepInstanceId(fromStep.id)) return
        val target = toStepIndex
        transitionRepository.save(
            WorkflowStepTransition().apply {
                instanceId = instance.id
                fromStepInstanceId = fromStep.id
                fromStepIndex = fromStep.stepIndex
                this.toStepIndex = target
                this.outcome = outcome
                recordedAt = Timestamp.from(Instant.now())
            }
        )
    }

    private fun emitDefinitionTerminalEvent(instance: WorkflowInstance, success: Boolean)
    {
        val explicit = instanceExecutionSpec(instance)
            ?.let { if (success) it.onComplete else it.onReject }
            ?.takeIf { it.isNotBlank() }

        val event = explicit ?: defaultTerminalEvent(instance.triggerEventSnapshot, success)
        event?.let { publishOutcomeEvent(instance, it) }

        val rt = instance.subjectResourceType
        val rid = instance.subjectResourceId
        if (rt != null && rid != null) scheduleCounterpartySweep(rt, rid)
    }

    private fun scheduleCounterpartySweep(resourceType: String, resourceId: UUID)
    {
        if (!::transactionSynchronizationRegistry.isInitialized || !::self.isInitialized)
        {
            unblockWaitingCounterpartySteps(resourceType, resourceId)
            return
        }

        transactionSynchronizationRegistry.registerInterposedSynchronization(object : Synchronization
        {
            override fun beforeCompletion() = Unit

            override fun afterCompletion(status: Int)
            {
                if (status != Status.STATUS_COMMITTED) return
                runCatching { self.unblockWaitingCounterpartySteps(resourceType, resourceId) }
                    .onFailure { e ->
                        logger.warn(
                            "Counterparty unblock sweep failed for subject {}/{}: {}",
                            resourceType,
                            resourceId,
                            e.message,
                        )
                    }
            }
        })
    }

    private fun defaultTerminalEvent(triggerEvent: String?, success: Boolean): String? =
        when (triggerEvent)
        {
            "exchange.acceptance_pending" -> if (success) "exchange.activated" else "session.rejected"
            "exchange.draft_submitted" -> if (success) "exchange.draft_approved" else "session.rejected"
            "exchange.ending" -> if (success) "exchange.ended_confirmed" else null
            else -> null
        }

    private fun decodeSubjectData(jsonStr: String?): Map<String, String>
    {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try
        {
            val obj = json.parseToJsonElement(jsonStr) as? kotlinx.serialization.json.JsonObject ?: return emptyMap()
            obj.entries.mapNotNull { (k, v) ->
                val prim = v as? kotlinx.serialization.json.JsonPrimitive ?: return@mapNotNull null
                k to prim.content
            }.toMap()
        }
        catch (_: Exception)
        {
            emptyMap()
        }
    }

    private fun workflowSubjectFields(triggerEvent: String): List<WorkflowSubjectField> =
        triggerEventRepository.findByEventName(triggerEvent)?.subjectFieldsJson?.let { fieldsJson ->
            runCatching {
                json.decodeFromString(ListSerializer(WorkflowSubjectField.serializer()), fieldsJson)
            }.getOrDefault(emptyList())
        }.orEmpty()

    private fun createStepInstance(
        instance: WorkflowInstance,
        index: Int,
        spec: WorkflowStepSpec,
    ): WorkflowStepInstance
    {
        val now = Timestamp.from(Instant.now())
        return WorkflowStepInstance().apply {
            instanceId = instance.id
            stepIndex = index
            stepType = spec.type
            status = WorkflowStepStatus.PENDING
            specSnapshotJson = WorkflowSpecJson.encodeStep(spec)
            addonsStateJson = "{}"
            dueAt = spec.slaMinutes?.let { Timestamp.from(now.toInstant().plusSeconds(it * 60L)) }
            createdAt = now
        }
    }

    private fun replaceAssignees(stepInstanceId: UUID, resolved: List<PrincipalRef>)
    {
        assigneeRepository.deleteAllByStepInstanceId(stepInstanceId)
        resolved.forEach { p ->
            assigneeRepository.save(
                com.docuhyphen.app.api.model.entity.WorkflowStepAssignee().apply {
                    this.stepInstanceId = stepInstanceId
                    this.principalKind = p.kind
                    this.principalId = p.id
                }
            )
        }
    }

    private fun quorumUnsatisfiable(spec: WorkflowStepSpec, resolvedCount: Int): Boolean
    {
        if (spec.type != WorkflowStepType.APPROVAL) return false
        val quorum = spec.quorum
        return quorum is QuorumSpec.NOfM && quorum.n > resolvedCount
    }

    private fun quorumMet(
        approvals: Int,
        quorum: QuorumSpec,
        assigneeTotal: Int,
    ): Boolean
    {
        return when (quorum)
        {
            is QuorumSpec.Any -> approvals >= 1
            is QuorumSpec.All -> approvals >= assigneeTotal && assigneeTotal > 0
            is QuorumSpec.NOfM -> approvals >= quorum.n
        }
    }

    private fun encodeStringMap(map: Map<String, String>): String =
        json.encodeToString(MapSerializer(String.serializer(), String.serializer()), map)

    private fun decodeAddonsState(jsonStr: String): Map<String, Map<String, String>>
    {
        if (jsonStr.isBlank() || jsonStr == "{}") return emptyMap()
        return runCatching {
            json.decodeFromString(
                MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())),
                jsonStr,
            )
        }.getOrDefault(emptyMap())
    }

    private fun encodeAddonsState(state: Map<String, Map<String, String>>): String =
        json.encodeToString(
            MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer())),
            state,
        )

    private fun publishStepAssigned(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        assignees: List<PrincipalRef>,
    )
    {
        if (assignees.isEmpty()) return
        val subjectFields = runCatching {
            instance.subjectDataJson?.let {
                json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
            }
        }.getOrNull() ?: emptyMap()
        val workflowName = runCatching {
            definitionRepository.findById(instance.definitionId)?.name
        }.getOrNull()
        val payload = buildMap {
            put("instanceId", instance.id.toString())
            put("stepInstanceId", step.id.toString())
            put("stepIndex", step.stepIndex.toString())
            put("assignees", assignees.joinToString(",") { "${it.kind.name}:${it.id}" })
            subjectFields["exchangeName"]?.takeIf { it.isNotBlank() }?.let { put("exchangeName", it) }
            subjectFields["initiatorName"]?.takeIf { it.isNotBlank() }?.let { put("initiatorName", it) }
            workflowName?.let { put("workflowName", it) }
        }
        eventPublisher.publish(
            DomainEvent(
                type = "workflow.step_assigned",
                organizationId = instance.organizationId?.toString(),
                subject = instance.subjectResourceType?.let { type ->
                    instance.subjectResourceId?.let { id ->
                        DomainEvent.SubjectRef(type, id.toString())
                    }
                },
                payload = payload,
            ),
            ownerOf(instance),
        )
    }

    private fun markWorkflowAssignmentNotificationRead(decider: PrincipalRef, stepInstanceId: UUID)
    {
        if (decider.kind != PrincipalKind.USER)
        {
            return
        }
        if (!::inAppNotificationService.isInitialized)
        {
            return
        }

        inAppNotificationService.markAsRead(
            appUserId = decider.id,
            criteria = NotificationReadCriteria(
                eventTypes = setOf("workflow.step_assigned", "workflow.escalated"),
                data = mapOf("stepInstanceId" to stepInstanceId.toString()),
            ),
        )
    }

    private fun publishOutcomeEvent(instance: WorkflowInstance, eventType: String)
    {
        val payload = HashMap<String, String>(2)
        payload["instanceId"] = instance.id.toString()
        val subjectFields = runCatching {
            instance.subjectDataJson?.let {
                json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
            }
        }.getOrNull() ?: emptyMap()
        subjectFields["initiatorId"]?.let { payload["initiator"] = "USER:$it" }

        eventPublisher.publish(
            DomainEvent(
                type = eventType,
                organizationId = instance.organizationId?.toString(),
                subject = instance.subjectResourceType?.let { type ->
                    instance.subjectResourceId?.let { id ->
                        DomainEvent.SubjectRef(type, id.toString())
                    }
                },
                payload = payload,
            ),
            ownerOf(instance),
        )
    }

    private fun publishEscalated(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        newAssignees: List<PrincipalRef>,
    )
    {
        val subjectFields = runCatching {
            instance.subjectDataJson?.let {
                json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
            }
        }.getOrNull() ?: emptyMap()
        val workflowName = runCatching {
            definitionRepository.findById(instance.definitionId)?.name
        }.getOrNull()
        val payload = mutableMapOf(
            "instanceId" to instance.id.toString(),
            "stepInstanceId" to step.id.toString(),
            "stepIndex" to step.stepIndex.toString(),
        )
        if (newAssignees.isNotEmpty())
        {
            payload["assignees"] = newAssignees.joinToString(",") { "${it.kind.name}:${it.id}" }
        }
        subjectFields["exchangeName"]?.takeIf { it.isNotBlank() }?.let { payload["exchangeName"] = it }
        subjectFields["initiatorName"]?.takeIf { it.isNotBlank() }?.let { payload["initiatorName"] = it }
        workflowName?.let { payload["workflowName"] = it }
        eventPublisher.publish(
            DomainEvent(
                type = "workflow.escalated",
                organizationId = instance.organizationId?.toString(),
                subject = instance.subjectResourceType?.let { type ->
                    instance.subjectResourceId?.let { id ->
                        DomainEvent.SubjectRef(type, id.toString())
                    }
                },
                payload = payload,
            ),
            ownerOf(instance),
        )
    }

    private fun publishFailed(instance: WorkflowInstance, code: String)
    {
        val payload = mutableMapOf(
            "instanceId" to instance.id.toString(),
            "failureCode" to code,
        )
        eventPublisher.publish(
            DomainEvent(
                type = "workflow.failed",
                organizationId = instance.organizationId?.toString(),
                subject = instance.subjectResourceType?.let { type ->
                    instance.subjectResourceId?.let { id ->
                        DomainEvent.SubjectRef(type, id.toString())
                    }
                },
                payload = payload,
            ),
            ownerOf(instance),
        )
    }

    private fun ownerOf(instance: WorkflowInstance): AuditOwnerScope {
        instance.organizationId?.let { return AuditOwnerScope.Organization(it) }
        val personalOwner = runCatching { definitionRepository.findById(instance.definitionId) }.getOrNull()
            ?.takeIf { it.scope == WorkflowScope.PERSONAL }
            ?.createdByAppUserId
        return personalOwner?.let(AuditOwnerScope::Personal) ?: AuditOwnerScope.Platform
    }

    @Suppress("unused")
    private val keepPrincipalKindReferenced: PrincipalKind = PrincipalKind.USER

    private companion object
    {
        const val FAILURE_SNAPSHOT_MISSING = "SNAPSHOT_MISSING"

        const val FAILURE_SNAPSHOT_CORRUPT = "SNAPSHOT_CORRUPT"

        const val FAILURE_ROUTE_INVALID = "ROUTE_INVALID"

        const val FAILURE_QUORUM_UNSATISFIABLE = "QUORUM_UNSATISFIABLE"

        const val SUBJECT_REFERENCE_PREFIX = "\$subject."
    }
}

