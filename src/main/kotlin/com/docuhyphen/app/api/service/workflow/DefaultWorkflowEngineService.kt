package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepStatus
import com.docuhyphen.app.api.repository.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Default [WorkflowEngineService] implementation.
 *
 * Deliberately conservative for iteration 2:
 *   * Triggering and decisions are synchronous (transactional).
 *   * Event "emission" returns the list to the caller (iteration 3 wires Kafka).
 *   * SLA escalation is a no-op marker (iteration 3 wires a Quarkus scheduler).
 *   * Only APPROVAL steps are exercised. NOTIFICATION / CONDITION / ACTION steps
 *     decode and advance unconditionally, concrete handlers land later.
 */
@ApplicationScoped
class DefaultWorkflowEngineService : WorkflowEngineService
{
    private val logger = LoggerFactory.getLogger(DefaultWorkflowEngineService::class.java)

    @Inject private lateinit var definitionRepository: WorkflowDefinitionRepository
    @Inject private lateinit var instanceRepository: WorkflowInstanceRepository
    @Inject private lateinit var stepRepository: WorkflowStepInstanceRepository
    @Inject private lateinit var assigneeResolver: WorkflowAssigneeResolver
    @Inject private lateinit var eventPublisher: DomainEventPublisher
    @Inject private lateinit var principalGroupMemberRepository: com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
    @Inject private lateinit var principalGroupRepository: com.docuhyphen.app.api.repository.PrincipalGroupRepository
    @Inject private lateinit var sharingSessionRepository: com.docuhyphen.app.api.repository.SharingSessionRepository
    @Inject private lateinit var appUserRepository: com.docuhyphen.app.api.repository.AppUserRepository

    private val json = WorkflowSpecJson.instance

    // -------------------------------------------------------------------------
    // trigger
    // -------------------------------------------------------------------------

    @Transactional
    override fun trigger(request: TriggerRequest): TriggerResult?
    {
        val definition = definitionRepository.findActiveForTrigger(request.triggerEvent, request.organizationId)
        if (definition == null)
        {
            logger.debug("No active workflow definition for trigger={} org={}", request.triggerEvent, request.organizationId)
            return null
        }

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
        val firstStep = spec.steps[0]
        val resolved = assigneeResolver.resolveAll(firstStep.assignees, instance.subjectDataJson)
        val stepInstance = createStepInstance(instance, 0, firstStep, resolved)
        stepRepository.save(stepInstance)

        // Notify assignees that they have a new task awaiting decision.
        publishStepAssigned(instance, stepInstance, resolved)

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
        val assignees = decodePrincipalList(step.assigneesSnapshotJson)
        if (assignees.none { it.kind == decider.kind.name && it.id == decider.id.toString() })
        {
            throw IllegalStateException("Decider ${decider.kind}/${decider.id} is not an assignee of step $stepInstanceId")
        }

        // Append the decision (idempotency: same decider voting twice replaces their vote).
        val existing = decodeDecisions(step.decisionsJson)
            .filterNot { it.principalKind == decider.kind.name && it.principalId == decider.id.toString() }
        val updated = existing + DecisionEntry(
            principalKind = decider.kind.name,
            principalId = decider.id.toString(),
            decision = decision.name,
            reason = reason,
            atEpochMillis = System.currentTimeMillis(),
        )
        step.decisionsJson = encodeDecisions(updated)

        val spec = WorkflowSpecJson.decodeStep(step.specSnapshotJson)
        val emitted = mutableListOf<String>()
        val now = Timestamp.from(Instant.now())

        if (decision == Decision.REJECT)
        {
            step.status = WorkflowStepStatus.REJECTED
            step.completedAt = now
            instance.status = WorkflowInstanceStatus.REJECTED
            instance.completedAt = now
            spec.onReject?.emit?.let { emitted += it }
        }
        else if (quorumMet(updated, spec.quorum, assigneeTotal = assignees.size))
        {
            step.status = WorkflowStepStatus.APPROVED
            step.completedAt = now
            spec.onApprove?.emit?.let { emitted += it }

            val nextRef = spec.onApprove?.nextStep ?: "END"
            if (nextRef.equals("END", ignoreCase = true))
            {
                instance.status = WorkflowInstanceStatus.COMPLETED
                instance.completedAt = now
            }
            else
            {
                val nextIndex = nextRef.toIntOrNull()
                if (nextIndex == null)
                {
                    logger.warn("Step {} has invalid onApprove.nextStep='{}', completing instance", step.id, nextRef)
                    instance.status = WorkflowInstanceStatus.COMPLETED
                    instance.completedAt = now
                }
                else
                {
                    advanceToStep(instance, nextIndex)
                }
            }
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
                }
                EscalationAction.AUTO_APPROVE ->
                {
                    step.status = WorkflowStepStatus.APPROVED
                    step.completedAt = now
                    spec.onApprove?.emit?.let { publishOutcomeEvent(instance, it) }
                    val nextRef = spec.onApprove?.nextStep ?: "END"
                    if (nextRef.equals("END", ignoreCase = true))
                    {
                        instance.status = WorkflowInstanceStatus.COMPLETED
                        instance.completedAt = now
                    }
                    else
                    {
                        nextRef.toIntOrNull()?.let { advanceToStep(instance, it) }
                    }
                }
                EscalationAction.ESCALATE, null ->
                {
                    // Reassign by re-resolving the escalation targets and overwriting the snapshot.
                    val targets = escalation?.escalateTo
                        ?.let { assigneeResolver.resolveAll(it, instance.subjectDataJson) }
                        ?: emptyList()
                    if (targets.isNotEmpty())
                    {
                        step.assigneesSnapshotJson = encodePrincipalList(
                            targets.map { PrincipalRefDto(it.kind.name, it.id.toString()) }
                        )
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
    // helpers
    // -------------------------------------------------------------------------

    /**
     * Scans all PENDING steps and return those the given user can decide on,
     * either as a direct USER assignee or as an active member of an assignee PRINCIPAL_GROUP.
     * Steps the user has already voted on are filtered out (idempotent re-decision is allowed
     * by [recordDecision] but irrelevant for the inbox).
     */
    override fun listPendingForUser(appUserId: UUID): List<PendingWorkflowStepDto>
    {
        val userIdStr = appUserId.toString()
        val userGroupIds: Set<String> = principalGroupMemberRepository
            .findGroupsForPrincipal(PrincipalKind.USER, appUserId)
            .map { it.principalGroupId.toString() }
            .toSet()

        return stepRepository.findAllPending().mapNotNull { step ->
            val assignees = decodePrincipalList(step.assigneesSnapshotJson)
            val isAssigned = assignees.any { a ->
                when (a.kind)
                {
                    PrincipalKind.USER.name -> a.id == userIdStr
                    PrincipalKind.PRINCIPAL_GROUP.name -> a.id in userGroupIds
                    else -> false
                }
            }
            if (!isAssigned) return@mapNotNull null

            // Skip if the user already voted on this step (any decision, APPROVE or REJECT).
            val alreadyVoted = decodeDecisions(step.decisionsJson)
                .any { it.principalKind == PrincipalKind.USER.name && it.principalId == userIdStr }
            if (alreadyVoted) return@mapNotNull null

            val instance = instanceRepository.findById(step.instanceId) ?: return@mapNotNull null
            val sessionId = instance.subjectResourceId
            val session = sessionId?.let { sharingSessionRepository.findById(it) }
            val initiator = instance.initiatedByAppUserId?.let { appUserRepository.findById(it) }
            val groupName = decodeSubjectData(instance.subjectDataJson)["recipientGroupId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?.let { principalGroupRepository.findById(it)?.name }

            PendingWorkflowStepDto(
                stepInstanceId = step.id.toString(),
                workflowInstanceId = instance.id.toString(),
                stepType = step.stepType.name,
                sessionId = sessionId?.toString(),
                sessionName = session?.sessionName,
                requestedByEmail = initiator?.email,
                requestedByName = initiator?.person?.let { p ->
                    "${p.firstName.orEmpty()} ${p.lastName.orEmpty()}".trim().takeIf { it.isNotBlank() }
                },
                groupName = groupName,
                createdAtEpochMillis = step.createdAt.time,
            )
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

    private fun advanceToStep(instance: WorkflowInstance, nextIndex: Int)
    {
        val definition = definitionRepository.findById(instance.definitionId)
            ?: throw IllegalStateException("Definition ${instance.definitionId} missing")
        val spec = WorkflowSpecJson.decode(definition.stepsJson)
        if (nextIndex !in spec.steps.indices)
        {
            instance.status = WorkflowInstanceStatus.COMPLETED
            instance.completedAt = Timestamp.from(Instant.now())
            return
        }
        instance.currentStepIndex = nextIndex
        val nextSpec = spec.steps[nextIndex]
        val resolved = assigneeResolver.resolveAll(nextSpec.assignees, instance.subjectDataJson)
        val newStep = createStepInstance(instance, nextIndex, nextSpec, resolved)
        stepRepository.save(newStep)
        publishStepAssigned(instance, newStep, resolved)
    }

    private fun createStepInstance(
        instance: WorkflowInstance,
        index: Int,
        spec: WorkflowStepSpec,
        resolvedAssignees: List<PrincipalRef>,
    ): WorkflowStepInstance
    {
        val now = Timestamp.from(Instant.now())
        return WorkflowStepInstance().apply {
            instanceId = instance.id
            stepIndex = index
            stepType = spec.type
            status = WorkflowStepStatus.PENDING
            specSnapshotJson = WorkflowSpecJson.encodeStep(spec)
            assigneesSnapshotJson = encodePrincipalList(
                resolvedAssignees.map { PrincipalRefDto(it.kind.name, it.id.toString()) }
            )
            decisionsJson = "[]"
            dueAt = spec.slaMinutes?.let { Timestamp.from(now.toInstant().plusSeconds(it * 60L)) }
            createdAt = now
        }
    }

    private fun quorumMet(
        decisions: List<DecisionEntry>,
        quorum: QuorumSpec,
        assigneeTotal: Int,
    ): Boolean
    {
        val approvals = decisions.count { it.decision == Decision.APPROVE.name }
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

    private fun encodePrincipalList(list: List<PrincipalRefDto>): String =
        json.encodeToString(ListSerializer(PrincipalRefDto.serializer()), list)

    private fun decodePrincipalList(jsonStr: String?): List<PrincipalRefDto>
    {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PrincipalRefDto.serializer()), jsonStr)
        }.getOrDefault(emptyList())
    }

    private fun encodeDecisions(list: List<DecisionEntry>): String =
        json.encodeToString(ListSerializer(DecisionEntry.serializer()), list)

    private fun decodeDecisions(jsonStr: String): List<DecisionEntry> =
        runCatching {
            json.decodeFromString(ListSerializer(DecisionEntry.serializer()), jsonStr)
        }.getOrDefault(emptyList())

    /** Wire format inside `assignees_snapshot_json`. */
    @Serializable
    private data class PrincipalRefDto(val kind: String, val id: String)

    /** Wire format inside `decisions_json`. */
    @Serializable
    private data class DecisionEntry(
        val principalKind: String,
        val principalId: String,
        val decision: String,
        val reason: String? = null,
        val atEpochMillis: Long,
    )

    // ---- event publishing ---------------------------------------------------

    /**
     * Emit `workflow.step_assigned` so the notification rule engine can fan it out to
     * each resolved assignee. The assignees are serialised into the event payload as a
     * csv of `USER:<uuid>` tokens, the format [com.docuhyphen.app.api.service.notification.NotificationRuleEngine]
     * understands for `EVENT_PAYLOAD` rules.
     */
    private fun publishStepAssigned(
        instance: WorkflowInstance,
        step: WorkflowStepInstance,
        assignees: List<PrincipalRef>,
    )
    {
        if (assignees.isEmpty()) return
        val payload = mapOf(
            "instanceId" to instance.id.toString(),
            "stepInstanceId" to step.id.toString(),
            "stepIndex" to step.stepIndex.toString(),
            "assignees" to assignees.joinToString(",") { "${it.kind.name}:${it.id}" },
        )
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

    /**
     * Emit the outcome event declared on the step spec (e.g. `session.activated`). The
     * notification rule engine has app-level rules for `session.activated` / `session.rejected`
     * that route to the session initiator (carried in the payload as `initiator=USER:<uuid>`
     * if the subject data captured it at trigger-time).
     */
    private fun publishOutcomeEvent(instance: WorkflowInstance, eventType: String)
    {
        val payload = HashMap<String, String>(2)
        payload["instanceId"] = instance.id.toString()
        // If the subject was captured with an initiator field, surface it for routing.
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

    /**
     * Emit `workflow.escalated` after an SLA breach has reassigned a pending step. The
     * payload carries the new assignees so the notification rule engine can fan it out.
     */
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

    /** Suppress unused-warning hint for the import that's only referenced inside a serializer. */
    @Suppress("unused")
    private val keepPrincipalKindReferenced: PrincipalKind = PrincipalKind.USER
}





