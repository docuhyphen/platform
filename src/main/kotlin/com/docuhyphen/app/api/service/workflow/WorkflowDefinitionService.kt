package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.dto.ExchangeClearanceStatusDto
import com.docuhyphen.app.api.model.dto.PartyClearanceDto
import com.docuhyphen.app.api.model.dto.WorkflowDecisionResponseDto
import com.docuhyphen.app.api.model.dto.WorkflowDefinitionDto
import com.docuhyphen.app.api.model.dto.WorkflowDefinitionListItemDto
import com.docuhyphen.app.api.model.dto.WorkflowEntityRefDto
import com.docuhyphen.app.api.model.dto.WorkflowInstanceDetailResponseDto
import com.docuhyphen.app.api.model.dto.WorkflowInstanceListItemDto
import com.docuhyphen.app.api.model.dto.WorkflowPrincipalRefResponseDto
import com.docuhyphen.app.api.model.dto.WorkflowStepInstanceResponseDto
import com.docuhyphen.app.api.model.dto.WorkflowSubjectFieldResponseDto
import com.docuhyphen.app.api.model.dto.WorkflowTriggerEventResponseDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.model.entity.WorkflowInstanceStatus
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.RoleScopeType
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.WorkflowDefinitionRepository
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowTriggerEventRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.UserContactService
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Business logic for workflow definition CRUD, clone, and instance queries.
 *
 * All HTTP surface area lives in the WorkflowDefinitionResource JAX-RS class.
 * This service is purely domain logic: access control, portability scrubbing, blocking
 * mutations on in-flight instances, and mapping entities to response DTOs.
 */
@ApplicationScoped
class WorkflowDefinitionService @Inject constructor(
    private val definitionRepository: WorkflowDefinitionRepository,
    private val instanceRepository: WorkflowInstanceRepository,
    private val stepRepository: WorkflowStepInstanceRepository,
    private val assigneeRepository: com.docuhyphen.app.api.repository.WorkflowStepAssigneeRepository,
    private val decisionRepository: com.docuhyphen.app.api.repository.WorkflowStepDecisionRepository,
    private val triggerEventRepository: WorkflowTriggerEventRepository,
    private val adminActionGuardService: AdminActionGuardService,
    private val userContactService: UserContactService,
    private val principalGroupRepository: PrincipalGroupRepository,
    private val appUserService: AppUserService,
)
{
    private val logger = LoggerFactory.getLogger(WorkflowDefinitionService::class.java)
    private val json = WorkflowSpecJson.instance

    // -------------------------------------------------------------------------
    // Definitions - read
    // -------------------------------------------------------------------------

    /**
     * Returns definitions accessible to the caller: platform templates, the org's own
     * ORG-scoped definitions, and the caller's PERSONAL definitions. Supports optional
     * server-side filtering by [scope], [tag], [triggerEvent], and [isTemplate].
     *
     * When [showUnpublished] is false (the default for regular org members), ORG-scoped
     * definitions that have not yet been published are hidden. Org admins and app admins
     * pass [showUnpublished] = true to see all drafts. PERSONAL definitions are always
     * visible to their creator regardless of published state.
     */
    fun listDefinitions(
        callerUserId: UUID?,
        callerOrgId: UUID?,
        scope: String?,
        tag: String?,
        triggerEvent: String?,
        isTemplate: Boolean?,
        showUnpublished: Boolean = false,
    ): List<WorkflowDefinitionListItemDto>
    {
        return definitionRepository.findAllAccessibleForCaller(callerUserId, callerOrgId)
            .asSequence()
            .filter { showUnpublished || it.isTemplate || it.isPublished || it.scope == WorkflowScope.PERSONAL }
            .filter { scope == null || it.scope.name == scope.uppercase() }
            .filter { tag == null || decodeTags(it.generalTags).contains(tag) }
            .filter { triggerEvent == null || it.triggerEvent == triggerEvent }
            .filter { isTemplate == null || it.isTemplate == isTemplate }
            .map { it.toListItemDto() }
            .toList()
    }

    /**
     * Returns the full definition (including stepsJson) for [id]. The caller must be the
     * creator (PERSONAL), belong to the definition's org (ORG), or the definition must be
     * a platform template (APP).
     *
     * Non-admin callers are blocked from accessing unpublished ORG definitions.
     */
    fun getDefinition(id: UUID, callerUserId: UUID?, callerOrgId: UUID?, isAppAdmin: Boolean, showUnpublished: Boolean = false): WorkflowDefinitionDto
    {
        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkReadAccess(def, callerUserId, callerOrgId, isAppAdmin, showUnpublished)
        return def.toDto()
    }

    // -------------------------------------------------------------------------
    // Definitions - write
    // -------------------------------------------------------------------------

    /**
     * Creates a new workflow definition. Scope is resolved server-side:
     *   - APP_ADMIN defaults to APP (may pass scope=ORG/PERSONAL explicitly)
     *   - ORG_ADMIN defaults to ORG (may pass scope=PERSONAL explicitly)
     *   - Regular user defaults to PERSONAL
     */
    @Transactional
    fun createDefinition(
        request: CreateWorkflowDefinitionRequest,
        callerOrgId: UUID?,
        callerUserId: UUID,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): WorkflowDefinitionDto
    {
        validateStepsJson(request.stepsJson)
        val resolvedScope = resolveScope(request.scope, callerOrgId, isOrgAdmin, isAppAdmin)
        val def = WorkflowDefinition().apply {
            name = request.name.trim()
            summary = request.summary?.trim()
            triggerEvent = request.triggerEvent.trim()
            stepsJson = request.stepsJson
            generalTags = encodeTags(request.generalTags)
            isActive = request.isActive
            scope = resolvedScope
            organizationId = if (resolvedScope == WorkflowScope.ORG) callerOrgId else null
            isTemplate = if (isAppAdmin) request.isTemplate else false
            createdByAppUserId = callerUserId
        }
        return definitionRepository.save(def).toDto()
    }

    /**
     * Updates a workflow definition. Blocked when RUNNING instances reference this definition.
     * Only the creator (PERSONAL), owning org's admin (ORG), or APP_ADMIN may call this.
     */
    @Transactional
    fun updateDefinition(
        id: UUID,
        request: UpdateWorkflowDefinitionRequest,
        callerUserId: UUID?,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): WorkflowDefinitionDto
    {
        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkWriteAccess(def, callerUserId, callerOrgId, isAppAdmin)

        val running = instanceRepository.findRunningForDefinition(id)
        if (running.isNotEmpty())
        {
            throw IllegalStateException(
                "Cannot update definition while ${running.size} workflow instance(s) are running against it"
            )
        }

        request.name?.trim()?.let { if (it.isNotBlank()) def.name = it }
        request.summary?.let { def.summary = it.trim().ifBlank { null } }
        request.stepsJson?.let {
            validateStepsJson(it)
            def.stepsJson = it
        }
        request.generalTags?.let { def.generalTags = encodeTags(it) }
        request.isActive?.let { def.isActive = it }

        return definitionRepository.update(def).toDto()
    }

    /** Publishes or unpublishes a definition, controlling visibility to non-admin org members. */
    @Transactional
    fun patchPublished(
        id: UUID,
        isPublished: Boolean,
        callerUserId: UUID?,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): WorkflowDefinitionDto
    {
        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkWriteAccess(def, callerUserId, callerOrgId, isAppAdmin)
        def.isPublished = isPublished
        return definitionRepository.update(def).toDto()
    }

    /** Activates or deactivates a definition without a full PUT body. */
    @Transactional
    fun patchStatus(
        id: UUID,
        isActive: Boolean,
        callerUserId: UUID?,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): WorkflowDefinitionDto
    {
        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkWriteAccess(def, callerUserId, callerOrgId, isAppAdmin)
        def.isActive = isActive
        return definitionRepository.update(def).toDto()
    }

    /**
     * Soft-deletes a definition by setting isActive=false.
     * Blocked when RUNNING instances reference this definition.
     */
    @Transactional
    fun deleteDefinition(id: UUID, callerUserId: UUID?, callerOrgId: UUID?, isAppAdmin: Boolean, context: AdminApprovalContext)
    {
        adminActionGuardService.enforce(
            action = "ORG_WORKFLOW_DEFINITION_DELETE",
            actorId = null,
            context = context,
        )

        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkWriteAccess(def, callerUserId, callerOrgId, isAppAdmin)

        val running = instanceRepository.findRunningForDefinition(id)
        if (running.isNotEmpty())
        {
            throw IllegalStateException(
                "Cannot delete definition while ${running.size} workflow instance(s) are running against it"
            )
        }

        def.isDeleted = true
        def.isActive = false
        definitionRepository.update(def)
        logger.info("Workflow definition {} soft-deleted", id)
    }

    /**
     * Copies a definition into the caller's PERSONAL scope. Scrubs all [AssigneeSpec.Principal]
     * entries (hardcoded UUIDs) with ROLE placeholders to preserve portability.
     * The clone starts as inactive so the user can review before enabling.
     * Sets [WorkflowDefinition.sourceTemplateId] to track provenance.
     */
    @Transactional
    fun cloneDefinition(
        id: UUID,
        callerOrgId: UUID?,
        callerUserId: UUID,
        newName: String?,
    ): WorkflowDefinitionDto
    {
        val source = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")

        val canClone = source.isTemplate
            || (source.scope == WorkflowScope.PERSONAL && source.createdByAppUserId == callerUserId)
            || (source.scope == WorkflowScope.ORG && source.organizationId == callerOrgId)
        if (!canClone)
        {
            throw ForbiddenException("Cannot clone a definition that belongs to another user or organization")
        }

        val scrubbedStepsJson = scrubPrincipalUuids(source.stepsJson)
        val baseName = newName?.trim()?.ifBlank { null } ?: "${source.name} (copy)"
        val clone = WorkflowDefinition().apply {
            name = uniqueCloneName(baseName, callerUserId)
            summary = source.summary
            triggerEvent = source.triggerEvent
            stepsJson = scrubbedStepsJson
            generalTags = source.generalTags
            isActive = false
            scope = WorkflowScope.PERSONAL
            organizationId = null
            isTemplate = false
            sourceTemplateId = source.id
            createdByAppUserId = callerUserId
        }
        return definitionRepository.save(clone).toDto()
    }

    // -------------------------------------------------------------------------
    // Triggers
    // -------------------------------------------------------------------------

    fun listTriggers(): List<WorkflowTriggerEventResponseDto> =
        triggerEventRepository.findAll().map { it.toDto() }

    // -------------------------------------------------------------------------
    // Instances
    // -------------------------------------------------------------------------

    fun listInstances(
        callerOrgId: UUID,
        status: String?,
        subjectResourceType: String?,
        page: Int,
        pageSize: Int,
    ): List<WorkflowInstanceListItemDto>
    {
        val statusEnum = status?.let {
            runCatching { WorkflowInstanceStatus.valueOf(it.uppercase()) }.getOrElse {
                throw IllegalArgumentException("Invalid status filter: $it")
            }
        }
        return instanceRepository.findForOrg(callerOrgId, statusEnum, subjectResourceType, page, pageSize)
            .map { instance ->
                val defName = runCatching { definitionRepository.findById(instance.definitionId)?.name }.getOrNull()
                instance.toListItemDto(defName)
            }
    }

    fun listInstancesForSubject(resourceType: String, resourceId: UUID): List<WorkflowInstanceListItemDto> =
        instanceRepository.findForSubject(resourceType, resourceId)
            .map { instance ->
                val defName = runCatching { definitionRepository.findById(instance.definitionId)?.name }.getOrNull()
                instance.toListItemDto(defName)
            }

    fun listInstancesForSubject(resourceType: String, resourceId: UUID, organizationId: UUID): List<WorkflowInstanceListItemDto> =
        instanceRepository.findForSubject(resourceType, resourceId, organizationId)
            .map { instance ->
                val defName = runCatching { definitionRepository.findById(instance.definitionId)?.name }.getOrNull()
                instance.toListItemDto(defName)
            }

    fun listInstancesForSubject(
        resourceType: String,
        resourceId: UUID,
        organizationId: UUID,
        callerId: UUID,
    ): List<WorkflowInstanceListItemDto> =
        instanceRepository.findForSubjectIncludingCrossOrgPendingAssignee(resourceType, resourceId, organizationId, callerId)
            .map { instance ->
                val defName = runCatching { definitionRepository.findById(instance.definitionId)?.name }.getOrNull()
                instance.toListItemDto(defName)
            }

    fun getExchangeClearanceStatus(exchangeId: UUID, callerOrgId: UUID): ExchangeClearanceStatusDto
    {
        val myInstances = instanceRepository.findForSubject("EXCHANGE", exchangeId, callerOrgId)
        val counterpartyInstances = instanceRepository.findForSubjectExcludingOrg("EXCHANGE", exchangeId, callerOrgId)

        return ExchangeClearanceStatusDto(
            myOrg = aggregateClearance(myInstances),
            counterparties = counterpartyInstances
                .groupBy { it.organizationId }
                .values
                .map { aggregateClearance(it) },
        )
    }

    private fun aggregateClearance(instances: List<WorkflowInstance>): PartyClearanceDto
    {
        if (instances.isEmpty()) return PartyClearanceDto("NONE")
        val statuses = instances.map { it.status }
        return when
        {
            statuses.any { it == WorkflowInstanceStatus.RUNNING || it == WorkflowInstanceStatus.ESCALATED } -> PartyClearanceDto("RUNNING")
            statuses.any { it == WorkflowInstanceStatus.REJECTED || it == WorkflowInstanceStatus.CANCELLED } -> PartyClearanceDto("BLOCKED")
            statuses.all { it == WorkflowInstanceStatus.COMPLETED } -> PartyClearanceDto("CLEARED")
            else -> PartyClearanceDto("NONE")
        }
    }

    fun getInstanceDetail(id: UUID, callerOrgId: UUID?, isAppAdmin: Boolean): WorkflowInstanceDetailResponseDto
    {
        val instance = instanceRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow instance not found: $id")

        if (!isAppAdmin)
        {
            if (callerOrgId == null)
            {
                // No org membership → no workflow visibility.
                throw ForbiddenException("Access denied to workflow instance $id")
            }
            if (instance.organizationId != null && instance.organizationId != callerOrgId)
            {
                throw ForbiddenException("Access denied to workflow instance $id")
            }
        }

        val steps = stepRepository.findByInstance(instance.id)
        val defName = runCatching { definitionRepository.findById(instance.definitionId)?.name }.getOrNull()
        return instance.toDetailDto(defName, steps)
    }

    // -------------------------------------------------------------------------
    // Access control
    // -------------------------------------------------------------------------

    private fun checkReadAccess(
        def: WorkflowDefinition,
        callerUserId: UUID?,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
        showUnpublished: Boolean = false,
    )
    {
        if (isAppAdmin || def.isTemplate) return
        if (def.scope == WorkflowScope.PERSONAL)
        {
            if (callerUserId != null && def.createdByAppUserId == callerUserId) return
            throw ForbiddenException("Access denied to workflow definition ${def.id}")
        }
        if (callerOrgId != null && def.organizationId == callerOrgId)
        {
            if (showUnpublished || def.isPublished) return
            throw ForbiddenException("Access denied to workflow definition ${def.id}")
        }
        throw ForbiddenException("Access denied to workflow definition ${def.id}")
    }

    private fun checkWriteAccess(
        def: WorkflowDefinition,
        callerUserId: UUID?,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    )
    {
        if (isAppAdmin) return
        if (def.isTemplate) throw ForbiddenException("Platform templates cannot be modified directly; clone them instead")
        if (def.scope == WorkflowScope.PERSONAL)
        {
            if (callerUserId != null && def.createdByAppUserId == callerUserId) return
            throw ForbiddenException("Access denied to workflow definition ${def.id}")
        }
        if (callerOrgId == null || def.organizationId != callerOrgId)
        {
            throw ForbiddenException("Access denied to workflow definition ${def.id}")
        }
    }

    // -------------------------------------------------------------------------
    // Scope resolution
    // -------------------------------------------------------------------------

    private fun resolveScope(
        requestedScope: String?,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): WorkflowScope
    {
        if (requestedScope != null)
        {
            val parsed = runCatching { WorkflowScope.valueOf(requestedScope.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid scope: $requestedScope") }
            if (parsed == WorkflowScope.APP && !isAppAdmin)
                throw ForbiddenException("App admin role required to create APP-scoped workflows")
            if (parsed == WorkflowScope.ORG && !isOrgAdmin && !isAppAdmin)
                throw ForbiddenException("Org admin role required to create ORG-scoped workflows")
            return parsed
        }
        return when
        {
            isAppAdmin -> WorkflowScope.APP
            isOrgAdmin && callerOrgId != null -> WorkflowScope.ORG
            else -> WorkflowScope.PERSONAL
        }
    }

    // -------------------------------------------------------------------------
    // Clone name deduplication
    // -------------------------------------------------------------------------

    /**
     * Returns [base] if no PERSONAL definition with that name already exists for [creatorId]
     * at version 1, otherwise appends " 2", " 3", … until a free slot is found.
     */
    private fun uniqueCloneName(base: String, creatorId: UUID): String
    {
        if (definitionRepository.findByNameVersionAndCreator(base, 1, creatorId) == null) return base
        var suffix = 2
        while (true)
        {
            val candidate = "$base $suffix"
            if (definitionRepository.findByNameVersionAndCreator(candidate, 1, creatorId) == null) return candidate
            suffix++
        }
    }

    // -------------------------------------------------------------------------
    // UUID scrubbing for clone portability
    // -------------------------------------------------------------------------

    private val roleReplacement = AssigneeSpec.RoleAssignees(
        roleName = "REVIEWER",
        scopeType = RoleScopeType.ORG,
        scopeIdRef = "\$subject.orgId",
    )

    private fun scrubAssignee(assignee: AssigneeSpec): AssigneeSpec =
        if (assignee is AssigneeSpec.Principal) roleReplacement else assignee

    /**
     * Replaces every [AssigneeSpec.Principal] (hardcoded UUID) with a portable
     * ROLE placeholder so cloned definitions do not leak principal IDs across orgs.
     * Covers step assignees, escalation targets, and addon recipient refs.
     */
    private fun scrubPrincipalUuids(stepsJson: String): String
    {
        return runCatching {
            val spec = WorkflowSpecJson.decode(stepsJson)
            val scrubbed = spec.copy(
                steps = spec.steps.map { step ->
                    step.copy(
                        assignees = step.assignees.map(::scrubAssignee),
                        escalation = step.escalation?.let { esc ->
                            esc.copy(escalateTo = esc.escalateTo.map(::scrubAssignee))
                        },
                        addons = step.addons.map { addon ->
                            when (addon)
                            {
                                is StepAddonSpec.ReminderBeforeDue ->
                                    addon.copy(recipientRef = scrubAssignee(addon.recipientRef))
                                is StepAddonSpec.ReminderIfNoDecision ->
                                    addon.copy(recipientRef = scrubAssignee(addon.recipientRef))
                            }
                        },
                    )
                },
            )
            WorkflowSpecJson.encode(scrubbed)
        }.getOrElse { ex ->
            logger.warn("Failed to scrub stepsJson during clone; using original. Error: {}", ex.message)
            stepsJson
        }
    }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private fun decodeTags(tagsJson: String): List<String> =
        runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), tagsJson)
        }.getOrDefault(emptyList())

    private fun encodeTags(tags: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), tags)

    // Private data class mirroring the trigger-event registry subject-field JSON shape.
    @Serializable
    private data class SubjectFieldEntry(
        val name: String,
        val type: String,
        val description: String? = null,
        val lookupType: String? = null,
    )

    private fun decodeSubjectFields(jsonStr: String): List<SubjectFieldEntry> =
        runCatching {
            json.decodeFromString(ListSerializer(SubjectFieldEntry.serializer()), jsonStr)
        }.getOrDefault(emptyList())

    // -------------------------------------------------------------------------
    // Principal display-info resolution
    // -------------------------------------------------------------------------

    /** Returns (displayName, email) for a principal, or (null, null) when the id is not a user UUID. */
    private fun resolveDisplayInfo(kind: String, id: String): Pair<String?, String?>
    {
        // Engine stores PrincipalKind.name values; USER is the only kind whose id is an AppUser UUID.
        if (kind != "USER" && kind != "APP_USER" && kind != "PRINCIPAL") return Pair(null, null)
        return runCatching {
            val uuid = UUID.fromString(id)
            val user = appUserService.getById(uuid) ?: return Pair(null, null)
            val name = listOfNotNull(user.person?.firstName, user.person?.lastName)
                .joinToString(" ").ifBlank { null }
            Pair(name, user.email)
        }.getOrDefault(Pair(null, null))
    }

    // -------------------------------------------------------------------------
    // Entity-to-DTO mapping
    // -------------------------------------------------------------------------

    private fun WorkflowDefinition.toDto() = WorkflowDefinitionDto(
        id = id,
        name = name,
        summary = summary,
        description = description,
        triggerEvent = triggerEvent,
        version = version,
        scope = scope.name,
        isActive = isActive,
        isPublished = isPublished,
        isTemplate = isTemplate,
        generalTags = decodeTags(generalTags),
        organizationId = organizationId,
        sourceTemplateId = sourceTemplateId,
        createdByAppUserId = createdByAppUserId,
        stepsJson = stepsJson,
        createdAt = createdAt,
    )

    private fun WorkflowDefinition.toListItemDto() = WorkflowDefinitionListItemDto(
        id = id,
        name = name,
        summary = summary,
        triggerEvent = triggerEvent,
        version = version,
        scope = scope.name,
        isActive = isActive,
        isPublished = isPublished,
        isTemplate = isTemplate,
        generalTags = decodeTags(generalTags),
        organizationId = organizationId,
        sourceTemplateId = sourceTemplateId,
        createdAt = createdAt,
    )

    private fun WorkflowInstance.toListItemDto(defName: String?) = WorkflowInstanceListItemDto(
        id = id,
        definitionId = definitionId,
        definitionName = defName,
        subjectResourceType = subjectResourceType,
        subjectResourceId = subjectResourceId,
        status = status.name,
        currentStepIndex = currentStepIndex,
        createdAt = createdAt,
        completedAt = completedAt,
    )

    private fun WorkflowInstance.toDetailDto(
        defName: String?,
        steps: List<WorkflowStepInstance>,
    ) = WorkflowInstanceDetailResponseDto(
        id = id,
        definitionId = definitionId,
        definitionName = defName,
        subjectResourceType = subjectResourceType,
        subjectResourceId = subjectResourceId,
        status = status.name,
        currentStepIndex = currentStepIndex,
        steps = steps.map { it.toDto() },
        createdAt = createdAt,
        completedAt = completedAt,
    )

    private fun WorkflowStepInstance.toDto() = WorkflowStepInstanceResponseDto(
        id = id,
        stepIndex = stepIndex,
        stepType = stepType.name,
        status = status.name,
        assignees = assigneeRepository.findAllByStepInstanceId(id)
            .map { a ->
                val kind = a.principalKind.name
                val pid = a.principalId.toString()
                val (displayName, email) = resolveDisplayInfo(kind, pid)
                WorkflowPrincipalRefResponseDto(kind, pid, displayName, email)
            },
        decisions = decisionRepository.findAllByStepInstanceId(id).map {
            val kind = it.principalKind.name
            val pid = it.principalId.toString()
            val (displayName, email) = resolveDisplayInfo(kind, pid)
            WorkflowDecisionResponseDto(
                principalKind = kind,
                principalId = pid,
                decision = it.decision,
                reason = it.reason,
                atEpochMillis = it.decidedAt.time,
                displayName = displayName,
                email = email,
            )
        },
        dueAt = dueAt,
        escalatedAt = escalatedAt,
        completedAt = completedAt,
        createdAt = createdAt,
    )

    private fun com.docuhyphen.app.api.model.entity.WorkflowTriggerEventRegistry.toDto() =
        WorkflowTriggerEventResponseDto(
            eventName = eventName,
            description = description,
            subjectFields = decodeSubjectFields(subjectFieldsJson).map {
                WorkflowSubjectFieldResponseDto(it.name, it.type, it.description, it.lookupType)
            },
            isActive = isActive,
        )

    fun entityLookup(actor: AppUser, lookupType: String, query: String?): List<WorkflowEntityRefDto>
    {
        val q = query?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return when (lookupType)
        {
            "APP_USER" ->
                userContactService.searchContacts(actor.id, q, 20)
                    .filter { it.contactAppUserId != null }
                    .map {
                        val label = listOfNotNull(it.contactFirstName, it.contactLastName)
                            .joinToString(" ").ifBlank { it.contactEmail }
                        WorkflowEntityRefDto(
                            id = it.contactAppUserId.toString(),
                            label = label,
                            sublabel = it.contactEmail,
                        )
                    }
            "GROUP" ->
                principalGroupRepository.findByOwnerUser(actor.id)
                    .filter { it.name.contains(q, ignoreCase = true) }
                    .map {
                        WorkflowEntityRefDto(
                            id = it.id.toString(),
                            label = it.name,
                            sublabel = it.description,
                        )
                    }
            else -> emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private fun validateStepsJson(stepsJson: String)
    {
        runCatching { WorkflowSpecJson.decode(stepsJson) }
            .getOrElse { throw IllegalArgumentException("Invalid stepsJson: ${it.message}") }
    }
}

// ── Request DTOs shared with WorkflowDefinitionResource ──────────────────────

@Serializable
data class CreateWorkflowDefinitionRequest(
    val name: String,
    val summary: String? = null,
    val triggerEvent: String,
    val stepsJson: String,
    val generalTags: List<String> = emptyList(),
    val isActive: Boolean = true,
    val isTemplate: Boolean = false,
    val scope: String? = null,
)

@Serializable
data class UpdateWorkflowDefinitionRequest(
    val name: String? = null,
    val summary: String? = null,
    val stepsJson: String? = null,
    val generalTags: List<String>? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class PatchWorkflowStatusRequest(
    val isActive: Boolean,
)

@Serializable
data class PatchWorkflowPublishedRequest(
    val isPublished: Boolean,
)

@Serializable
data class CloneWorkflowRequest(
    val newName: String? = null,
)


