package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import com.docuhyphen.app.api.repository.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.AppNotificationService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.MarkdownRenderer
import com.docuhyphen.app.api.service.communication.templates.EmailTemplateRenderer
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.communication.CommunicationResolver
import com.docuhyphen.app.api.service.variable.VariableResolutionContext
import org.eclipse.microprofile.config.inject.ConfigProperty
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Default [WorkflowEngineService] implementation.
 *
 * Phase 2 additions over the original iteration-2 stub:
 *   * NOTIFICATION steps send in-app and email notifications to each resolved assignee,
 *     then auto-complete.
 *   * CONDITION steps evaluate a `predicateExpression` against `subjectDataJson` fields
 *     and follow the `onTrue` / `onFalse` outcome branch, then auto-complete.
 *   * ACTION steps dispatch to a registered [WorkflowActionHandler] by `actionHandlerKey`.
 *     On success the step completes; on failure the step and instance are REJECTED.
 *   * Addon processing ([StepAddonSpec]) is evaluated in `escalateOverdue()` so the
 *     scheduler tick covers both SLA escalation and reminder dispatch.
 */
@ApplicationScoped
class DefaultWorkflowEngineService : WorkflowEngineService
{
    private val logger = LoggerFactory.getLogger(DefaultWorkflowEngineService::class.java)

    @Inject private lateinit var definitionRepository: WorkflowDefinitionRepository
    @Inject private lateinit var instanceRepository: WorkflowInstanceRepository
    @Inject private lateinit var stepRepository: WorkflowStepInstanceRepository
    @Inject private lateinit var assigneeRepository: com.docuhyphen.app.api.repository.WorkflowStepAssigneeRepository
    @Inject private lateinit var decisionRepository: com.docuhyphen.app.api.repository.WorkflowStepDecisionRepository
    @Inject private lateinit var assigneeResolver: WorkflowAssigneeResolver
    @Inject private lateinit var eventPublisher: DomainEventPublisher
    @Inject private lateinit var principalGroupMemberRepository: com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
    @Inject private lateinit var principalGroupRepository: com.docuhyphen.app.api.repository.PrincipalGroupRepository
    @Inject private lateinit var exchangeRepository: com.docuhyphen.app.api.repository.ExchangeRepository
    @Inject private lateinit var appUserRepository: com.docuhyphen.app.api.repository.AppUserRepository
    @Inject private lateinit var appNotificationService: AppNotificationService
    @Inject private lateinit var emailService: EmailService
    @Inject private lateinit var communicationResolver: CommunicationResolver
    @Inject private lateinit var organizationRepository: com.docuhyphen.app.api.repository.OrganizationRepository
    @Inject private lateinit var markdownRenderer: MarkdownRenderer
    @Inject private lateinit var emailTemplateRenderer: EmailTemplateRenderer

    @ConfigProperty(name = "app.url", defaultValue = "https://app.docuhyphen.com")
    private lateinit var appUrl: String

    @ConfigProperty(name = "app.name", defaultValue = "DocuHyphen")
    private lateinit var appName: String

    /** CDI programmatic lookup of all registered [WorkflowActionHandler] beans. */
    @Inject private lateinit var actionHandlerBeans: Instance<WorkflowActionHandler>

    private val json = WorkflowSpecJson.instance

    /** Populated at startup; key = [WorkflowActionHandler.key()]. */
    private lateinit var actionHandlers: Map<String, WorkflowActionHandler>

    @PostConstruct
    fun buildActionHandlerRegistry()
    {
        actionHandlers = actionHandlerBeans.associate { it.key() to it }
        logger.info("Registered {} workflow action handler(s): {}", actionHandlers.size, actionHandlers.keys)
    }

    // -------------------------------------------------------------------------
    // trigger
    // -------------------------------------------------------------------------

    @Transactional
    override fun trigger(request: TriggerRequest): TriggerResult?
    {
        val definitions = definitionRepository.findAllActiveForTrigger(request.triggerEvent, request.organizationId)
        if (definitions.isEmpty())
        {
            logger.debug("No active workflow definition for trigger={} org={}", request.triggerEvent, request.organizationId)
            return null
        }

        var firstResult: TriggerResult? = null
        for (definition in definitions)
        {
            val result = triggerOne(definition, request)
            if (firstResult == null) firstResult = result
        }
        return firstResult
    }

    private fun triggerOne(definition: com.docuhyphen.app.api.model.entity.WorkflowDefinition, request: TriggerRequest): TriggerResult?
    {
        val spec = WorkflowSpecJson.decode(definition.stepsJson)
        if (spec.steps.isEmpty())
        {
            logger.warn("Workflow definition {} has no steps; skipping", definition.id)
            return null
        }

        // Persist the instance with a frozen subject snapshot.
        val instance = WorkflowInstance().apply {
            definitionId = definition.id
            definitionVersion = definition.version
            subjectResourceType = request.subjectResourceType
            subjectResourceId = request.subjectResourceId
            organizationId = request.organizationId
            status = WorkflowInstanceStatus.RUNNING
            currentStepIndex = 0
            subjectDataJson = encodeStringMap(request.subjectData)
            initiatedByAppUserId = request.initiatedByAppUserId
        }
        instanceRepository.save(instance)

        // Materialise the first step.
        val firstSpec = spec.steps[0]
        val resolved = assigneeResolver.resolveAll(firstSpec.assignees, instance.subjectDataJson)
        val stepInstance = createStepInstance(instance, 0, firstSpec)
        stepRepository.save(stepInstance)
        replaceAssignees(stepInstance.id, resolved)

        // Activate the step: APPROVAL steps wait for human decisions; all other types
        // execute immediately and may advance through subsequent steps in the same call.
        val autoEvents = activateStep(instance, stepInstance, firstSpec, resolved)
        if (stepInstance.status != WorkflowStepStatus.PENDING)
        {
            // Auto-advance step completed synchronously; persist the updated step and instance.
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

    // -------------------------------------------------------------------------
    // recordDecision
    // -------------------------------------------------------------------------

    @Transactional
    override fun recordDecision(
        stepInstanceId: UUID,
        decider: PrincipalRef,
        decision: Decision,
        reason: String?,
    ): DecisionResult
    {
        val step = stepRepository.findById(stepInstanceId)
            ?: throw IllegalArgumentException("Step instance $stepInstanceId not found")
        if (step.status != WorkflowStepStatus.PENDING)
        {
            throw IllegalStateException("Step $stepInstanceId is not PENDING (status=${step.status})")
        }
        val instance = instanceRepository.findById(step.instanceId)
            ?: throw IllegalStateException("Workflow instance ${step.instanceId} missing")

        // Authorise: decider must be one of the snapshotted assignees.
        val assignees = assigneeRepository.findAllByStepInstanceId(step.id)
        if (assignees.none { it.principalKind == decider.kind && it.principalId == decider.id })
        {
            throw IllegalStateException("Decider ${decider.kind}/${decider.id} is not an assignee of step $stepInstanceId")
        }

        val now = Timestamp.from(Instant.now())

        // Record the decision (idempotency: same decider voting twice replaces their vote).
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
            spec.onReject?.emit?.let { emitted += it }
            emitDefinitionTerminalEvent(instance, success = false)
        }
        else if (quorumMet(approvals, spec.quorum, assigneeTotal = assignees.size))
        {
            step.status = WorkflowStepStatus.APPROVED
            step.completedAt = now
            spec.onApprove?.emit?.let { emitted += it }
            advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step.id)
        }
        // else: quorum not yet met, leave step PENDING.

        stepRepository.update(step)
        instanceRepository.update(instance)

        // Fire all events outside the data-mutation block so a publisher failure
        // never rolls back the decision.
        emitted.forEach { publishOutcomeEvent(instance, it) }

        return DecisionResult(
            instanceId = instance.id,
            stepInstanceId = step.id,
            stepStatus = step.status,
            instanceStatus = instance.status,
            emittedEvents = emitted,
        )
    }

    // -------------------------------------------------------------------------
    // escalateOverdue / cancel
    // -------------------------------------------------------------------------

    @Transactional
    override fun escalateOverdue(now: Timestamp): Int
    {
        // Process addons (reminders) for every pending step before handling SLA escalation.
        stepRepository.findAllPending().forEach { step ->
            val instance = instanceRepository.findById(step.instanceId)
            if (instance != null && instance.status == WorkflowInstanceStatus.RUNNING)
            {
                val spec = WorkflowSpecJson.decodeStep(step.specSnapshotJson)
                if (spec.addons.isNotEmpty())
                {
                    processAddons(step, spec, now, instance)
                }
            }
        }

        val overdue = stepRepository.findPendingDueBefore(now)
        if (overdue.isEmpty()) return 0

        var escalated = 0
        for (step in overdue)
        {
            if (step.escalatedAt != null) continue   // already escalated this cycle
            val instance = instanceRepository.findById(step.instanceId) ?: continue

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
                    spec.onReject?.emit?.let { publishOutcomeEvent(instance, it) }
                    emitDefinitionTerminalEvent(instance, success = false)
                }
                EscalationAction.AUTO_APPROVE ->
                {
                    step.status = WorkflowStepStatus.APPROVED
                    step.completedAt = now
                    spec.onApprove?.emit?.let { publishOutcomeEvent(instance, it) }
                    advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step.id)
                }
                EscalationAction.ESCALATE, null ->
                {
                    // Reassign by re-resolving the escalation targets and overwriting the snapshot.
                    val targets = escalation?.escalateTo
                        ?.let { assigneeResolver.resolveAll(it, instance.subjectDataJson) }
                        ?: emptyList()
                    if (targets.isNotEmpty())
                    {
                        replaceAssignees(step.id, targets)
                        // Reset SLA so the new assignees get a fresh window.
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
        val instance = instanceRepository.findById(instanceId) ?: return
        if (instance.status != WorkflowInstanceStatus.RUNNING) return
        instance.status = WorkflowInstanceStatus.CANCELLED
        instance.completedAt = Timestamp.from(Instant.now())
        instanceRepository.update(instance)

        // Mark any pending step instance(s) as SKIPPED so they stop being assignable.
        stepRepository.findByInstance(instanceId)
            .filter { it.status == WorkflowStepStatus.PENDING }
            .forEach {
                it.status = WorkflowStepStatus.SKIPPED
                it.completedAt = instance.completedAt
                stepRepository.update(it)
            }
        if (reason != null)
        {
            logger.info("Workflow instance {} cancelled: {}", instanceId, reason)
        }
    }

    // -------------------------------------------------------------------------
    // listPendingForUser
    // -------------------------------------------------------------------------

    /**
     * Scans all PENDING steps and return those the given user can decide on,
     * either as a direct USER assignee or as an active member of an assignee PRINCIPAL_GROUP.
     * Steps the user has already voted on are filtered out (idempotent re-decision is allowed
     * by [recordDecision] but irrelevant for the inbox).
     */
    override fun listPendingForUser(appUserId: UUID): List<PendingWorkflowStepDto>
    {
        val userGroupIds: Set<UUID> = principalGroupMemberRepository
            .findGroupsForPrincipal(PrincipalKind.USER, appUserId)
            .map { it.principalGroupId }
            .toSet()

        return stepRepository.findPendingForAssignee(appUserId, userGroupIds).mapNotNull { step ->
            // Skip if the user already voted on this step (any decision, APPROVE or REJECT).
            val alreadyVoted = decisionRepository
                .findByStepAndPrincipal(step.id, PrincipalKind.USER, appUserId) != null
            if (alreadyVoted) return@mapNotNull null

            val instance = instanceRepository.findById(step.instanceId) ?: return@mapNotNull null
            val exchangeId = instance.subjectResourceId
            val session = exchangeId?.let { exchangeRepository.findById(it) }
            val initiator = instance.initiatedByAppUserId?.let { appUserRepository.findById(it) }
            val groupName = decodeSubjectData(instance.subjectDataJson)["recipientGroupId"]
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

    // -------------------------------------------------------------------------
    // Step activation and auto-advance handlers
    // -------------------------------------------------------------------------

    /**
     * Called once a step is created (persisted). For APPROVAL steps this notifies
     * assignees and leaves the step PENDING. For all other step types the step is
     * executed immediately and the list of emitted event strings is returned so the
     * caller can publish them.
     *
     * Note: for auto-advance steps the caller is responsible for persisting the
     * mutated step and instance via [stepRepository].update / [instanceRepository].update.
     */
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

    /**
     * Sends in-app and email notifications to every USER assignee in the step's snapshot,
     * then marks the step COMPLETED and follows the `onApprove` outcome.
     *
     * When [WorkflowStepSpec.communicationId] is set, the referenced [Communication]
     * is resolved and interpolated. The rendered subject and body are used for delivery.
     * Falls back to generic strings when the communication is missing, inactive, or fails to
     * resolve.
     */
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
        advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step.id)
        return emitted
    }

    /**
     * Evaluates [WorkflowStepSpec.predicateExpression] against the instance's subject data,
     * follows the `onTrue` or `onFalse` outcome, and marks the step COMPLETED.
     *
     * Supported operators: `==`, `!=`, `contains`, `startsWith`.
     * Syntax: `"$subject.<key> <op> '<value>'"`.
     */
    private fun executeConditionStep(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        spec: WorkflowStepSpec,
    ): List<String>
    {
        val now = Timestamp.from(Instant.now())
        val subjectData = decodeSubjectData(instance.subjectDataJson)
        val result = evaluatePredicate(spec.predicateExpression, subjectData)
        logger.debug("CONDITION step {} predicate='{}' evaluated to {}", step.id, spec.predicateExpression, result)

        step.status = WorkflowStepStatus.COMPLETED
        step.completedAt = now

        val outcome = if (result) spec.onTrue else spec.onFalse
        val emitted = mutableListOf<String>()
        outcome?.emit?.let { emitted += it }
        advanceOrComplete(instance, outcome?.nextStep ?: "END", now, step.id)
        return emitted
    }

    /**
     * Dispatches the ACTION step to the [WorkflowActionHandler] registered for
     * [WorkflowStepSpec.actionHandlerKey]. On success the step is COMPLETED; on failure
     * the step and instance are REJECTED.
     */
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
            advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step.id)
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
            advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step.id)
        }
        else
        {
            logger.warn("ACTION step {} failed (handler='{}'): {}", step.id, handlerKey, result.reason)
            step.status = WorkflowStepStatus.REJECTED
            step.completedAt = now
            instance.status = WorkflowInstanceStatus.REJECTED
            instance.completedAt = now
            spec.onReject?.emit?.let { emitted += it }
            emitDefinitionTerminalEvent(instance, success = false)
        }
        return emitted
    }

    // -------------------------------------------------------------------------
    // WAIT_FOR_COUNTERPARTY_CLEARANCE step
    // -------------------------------------------------------------------------

    /**
     * Evaluates whether any counterparty workflow instances on the same exchange subject are
     * still running. If yes, parks the step as AWAITING_COUNTERPARTY; if no (or no counterparty
     * instances exist), completes the step immediately and advances.
     */
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
            instanceRepository.findForSubjectExcludingOrg(rt, rid, myOrgId)
                .any { it.status == WorkflowInstanceStatus.RUNNING || it.status == WorkflowInstanceStatus.ESCALATED }
        else
            false

        if (counterpartyRunning)
        {
            step.status = WorkflowStepStatus.AWAITING_COUNTERPARTY
            logger.info("Step {} parked AWAITING_COUNTERPARTY for exchange {}", step.id, rid)
            return emptyList()
        }

        val now = java.sql.Timestamp.from(java.time.Instant.now())
        step.status = WorkflowStepStatus.COMPLETED
        step.completedAt = now
        val emitted = mutableListOf<String>()
        spec.onApprove?.emit?.let { emitted += it }
        advanceOrComplete(instance, spec.onApprove?.nextStep ?: "END", now, step.id)
        return emitted
    }

    /**
     * After any instance on the given subject reaches a terminal state, re-evaluates all
     * AWAITING_COUNTERPARTY steps for the same subject. Steps whose counterparties are now all
     * terminal are unblocked and the workflow advances.
     *
     * Called by [emitDefinitionTerminalEvent] so the sweep happens on every terminal transition.
     */
    @Transactional
    fun unblockWaitingCounterpartySteps(resourceType: String, resourceId: java.util.UUID)
    {
        val waiting = stepRepository.findAwaitingCounterpartyForSubject(resourceType, resourceId)
        if (waiting.isEmpty()) return

        for (step in waiting)
        {
            val parentInstance = instanceRepository.findById(step.instanceId) ?: continue
            if (parentInstance.status != WorkflowInstanceStatus.RUNNING) continue

            val myOrgId = parentInstance.organizationId
            val counterpartyRunning = if (myOrgId != null)
                instanceRepository.findForSubjectExcludingOrg(resourceType, resourceId, myOrgId)
                    .any { it.status == WorkflowInstanceStatus.RUNNING || it.status == WorkflowInstanceStatus.ESCALATED }
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
                advanceOrComplete(parentInstance, spec.onApprove?.nextStep ?: "END", now, step.id)
                instanceRepository.update(parentInstance)
                emitted.forEach { publishOutcomeEvent(parentInstance, it) }
                logger.info("Unblocked AWAITING_COUNTERPARTY step {} for instance {}", step.id, parentInstance.id)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Addon processing (reminders)
    // -------------------------------------------------------------------------

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
                        logger.debug("REMINDER_IF_NO_DECISION addon {} fired (count={}) for step {}", idx, fireCount + 1, step.id)
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
                    try { emailService.sendEmail(user.email, subject, body) }
                    catch (e: Exception) { logger.warn("Reminder email failed for {}: {}", user.email, e.message) }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Routing helpers
    // -------------------------------------------------------------------------

    private fun advanceOrComplete(
        instance: WorkflowInstance,
        nextRef: String,
        now: Timestamp,
        currentStepId: UUID,
    )
    {
        if (nextRef.equals("END", ignoreCase = true))
        {
            instance.status = WorkflowInstanceStatus.COMPLETED
            instance.completedAt = now
            emitDefinitionTerminalEvent(instance, success = true)
        }
        else
        {
            val nextIndex = nextRef.toIntOrNull()
            if (nextIndex == null)
            {
                logger.warn("Step {} has invalid nextStep='{}'; completing instance", currentStepId, nextRef)
                instance.status = WorkflowInstanceStatus.COMPLETED
                instance.completedAt = now
                emitDefinitionTerminalEvent(instance, success = true)
            }
            else
            {
                advanceToStep(instance, nextIndex)
            }
        }
    }

    private fun advanceToStep(instance: WorkflowInstance, nextIndex: Int)
    {
        val definition = definitionRepository.findById(instance.definitionId)
            ?: throw IllegalStateException("Definition ${instance.definitionId} missing")
        val spec = WorkflowSpecJson.decode(definition.stepsJson)
        if (nextIndex !in spec.steps.indices)
        {
            instance.status = WorkflowInstanceStatus.COMPLETED
            instance.completedAt = Timestamp.from(Instant.now())
            spec.onComplete?.let { publishOutcomeEvent(instance, it) }
            return
        }
        instance.currentStepIndex = nextIndex
        val nextSpec = spec.steps[nextIndex]
        val resolved = assigneeResolver.resolveAll(nextSpec.assignees, instance.subjectDataJson)
        val newStep = createStepInstance(instance, nextIndex, nextSpec)
        stepRepository.save(newStep)
        replaceAssignees(newStep.id, resolved)

        val autoEvents = activateStep(instance, newStep, nextSpec, resolved)
        if (newStep.status != WorkflowStepStatus.PENDING)
        {
            stepRepository.update(newStep)
            autoEvents.forEach { publishOutcomeEvent(instance, it) }
        }
    }

    /**
     * Emits the definition-level terminal event ([WorkflowSpec.onComplete] or [WorkflowSpec.onReject])
     * when the instance reaches a terminal state. This fires unconditionally so the correct
     * outcome event is always published regardless of how individual steps are configured —
     * the handlers are idempotent so a step-level emit on the same event name is harmless.
     *
     * Also triggers the counterparty-clearance unblock sweep so any AWAITING_COUNTERPARTY steps
     * on the same exchange subject are re-evaluated.
     */
    private fun emitDefinitionTerminalEvent(instance: WorkflowInstance, success: Boolean)
    {
        val event = runCatching {
            definitionRepository.findById(instance.definitionId)
                ?.let { WorkflowSpecJson.decode(it.stepsJson) }
                ?.let { if (success) it.onComplete else it.onReject }
        }.getOrNull() ?: return
        event?.let { publishOutcomeEvent(instance, it) }

        // Re-evaluate any AWAITING_COUNTERPARTY steps for the same exchange subject.
        val rt = instance.subjectResourceType
        val rid = instance.subjectResourceId
        if (rt != null && rid != null)
        {
            runCatching { unblockWaitingCounterpartySteps(rt, rid) }
                .onFailure { e -> logger.warn("Counterparty unblock sweep failed for subject {}/{}: {}", rt, rid, e.message) }
        }
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
        catch (_: Exception) { emptyMap() }
    }

    private fun evaluatePredicate(expression: String?, subjectData: Map<String, String>): Boolean
    {
        if (expression.isNullOrBlank()) return true
        val trimmed = expression.trim()
        val operators = listOf("startsWith", "contains", "!=", "==")
        for (op in operators)
        {
            val delimiter = " $op "
            val idx = trimmed.indexOf(delimiter)
            if (idx < 0) continue
            val fieldRef = trimmed.substring(0, idx).trim()
            val expected = trimmed.substring(idx + delimiter.length).trim().removeSurrounding("'")
            val fieldName = fieldRef.removePrefix("\$subject.").trim()
            val actual = subjectData[fieldName] ?: ""
            return when (op)
            {
                "==" -> actual == expected
                "!=" -> actual != expected
                "contains" -> actual.contains(expected)
                "startsWith" -> actual.startsWith(expected)
                else -> false
            }
        }
        logger.warn("Could not parse predicate expression '{}'; defaulting to true", trimmed)
        return true
    }

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

    /**
     * Replaces the persisted assignee rows for [stepInstanceId] with [resolved]. Used both
     * when a step is first materialised and when SLA escalation reassigns the step.
     */
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

    // ---- JSON helpers -------------------------------------------------------

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

    // ---- event publishing ---------------------------------------------------

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
            )
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
            )
        )
    }

    private fun publishEscalated(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        newAssignees: List<PrincipalRef>,
    )
    {
        val payload = mutableMapOf(
            "instanceId" to instance.id.toString(),
            "stepInstanceId" to step.id.toString(),
            "stepIndex" to step.stepIndex.toString(),
        )
        if (newAssignees.isNotEmpty())
        {
            payload["assignees"] = newAssignees.joinToString(",") { "${it.kind.name}:${it.id}" }
        }
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
            )
        )
    }

    @Suppress("unused")
    private val keepPrincipalKindReferenced: PrincipalKind = PrincipalKind.USER
}

