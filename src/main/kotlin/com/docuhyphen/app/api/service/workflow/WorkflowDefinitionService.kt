package com.docuhyphen.app.api.service.workflow

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
    private val triggerEventRepository: WorkflowTriggerEventRepository,
    private val adminActionGuardService: AdminActionGuardService,
    private val userContactService: UserContactService,
    private val principalGroupRepository: PrincipalGroupRepository,
)
{
    private val logger = LoggerFactory.getLogger(WorkflowDefinitionService::class.java)
    private val json = WorkflowSpecJson.instance

    // -------------------------------------------------------------------------
    // Definitions - read
    // -------------------------------------------------------------------------

    /**
     * Returns definitions accessible to the caller: platform templates plus the org's own
     * definitions. Supports optional server-side filtering by [tag], [triggerEvent], and
     * [isTemplate].
     *
     * When [showUnpublished] is false (the default for regular org members), ORG-scoped
     * definitions that have not yet been published are hidden. Org admins and app admins
     * pass [showUnpublished] = true to see all drafts.
     */
    fun listDefinitions(
        callerOrgId: UUID?,
        tag: String?,
        triggerEvent: String?,
        isTemplate: Boolean?,
        showUnpublished: Boolean = false,
    ): List<WorkflowDefinitionListItemDto>
    {
        return definitionRepository.findAllAccessibleForOrg(callerOrgId)
            .asSequence()
            .filter { showUnpublished || it.isTemplate || it.isPublished }
            .filter { tag == null || decodeTags(it.generalTags).contains(tag) }
            .filter { triggerEvent == null || it.triggerEvent == triggerEvent }
            .filter { isTemplate == null || it.isTemplate == isTemplate }
            .map { it.toListItemDto() }
            .toList()
    }

    /**
     * Returns the full definition (including stepsJson) for [id]. The caller must belong
     * to the definition's org, or the definition must be a platform template.
     *
     * Non-admin callers are blocked from accessing unpublished definitions.
     */
    fun getDefinition(id: UUID, callerOrgId: UUID?, isAppAdmin: Boolean, showUnpublished: Boolean = false): WorkflowDefinitionDto
    {
        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkReadAccess(def, callerOrgId, isAppAdmin, showUnpublished)
        return def.toDto()
    }

    // -------------------------------------------------------------------------
    // Definitions - write
    // -------------------------------------------------------------------------

    /**
     * Creates a new ORG-scoped workflow definition. The scope is always forced to ORG for
     * non-APP_ADMIN callers. APP_ADMIN callers may create platform templates by passing
     * [CreateWorkflowDefinitionRequest.isTemplate] = true.
     */
    @Transactional
    fun createDefinition(
        request: CreateWorkflowDefinitionRequest,
        callerOrgId: UUID,
        callerUserId: UUID,
        isAppAdmin: Boolean,
    ): WorkflowDefinitionDto
    {
        validateStepsJson(request.stepsJson)
        val def = WorkflowDefinition().apply {
            name = request.name.trim()
            summary = request.summary?.trim()
            triggerEvent = request.triggerEvent.trim()
            stepsJson = request.stepsJson
            generalTags = encodeTags(request.generalTags)
            isActive = request.isActive
            scope = WorkflowScope.ORG
            organizationId = callerOrgId
            isTemplate = if (isAppAdmin) request.isTemplate else false
            createdByAppUserId = callerUserId
        }
        return definitionRepository.save(def).toDto()
    }

    /**
     * Updates a workflow definition. Blocked when RUNNING instances reference this definition.
     * Only the owning org's admin (or APP_ADMIN for platform templates) may call this.
     */
    @Transactional
    fun updateDefinition(
        id: UUID,
        request: UpdateWorkflowDefinitionRequest,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): WorkflowDefinitionDto
    {
        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkWriteAccess(def, callerOrgId, isAppAdmin)

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
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): WorkflowDefinitionDto
    {
        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkWriteAccess(def, callerOrgId, isAppAdmin)
        def.isPublished = isPublished
        return definitionRepository.update(def).toDto()
    }

    /** Activates or deactivates a definition without a full PUT body. */
    @Transactional
    fun patchStatus(
        id: UUID,
        isActive: Boolean,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): WorkflowDefinitionDto
    {
        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkWriteAccess(def, callerOrgId, isAppAdmin)
        def.isActive = isActive
        return definitionRepository.update(def).toDto()
    }

    /**
     * Soft-deletes a definition by setting isActive=false.
     * Blocked when RUNNING instances reference this definition.
     */
    @Transactional
    fun deleteDefinition(id: UUID, callerOrgId: UUID?, isAppAdmin: Boolean, context: AdminApprovalContext)
    {
        adminActionGuardService.enforce(
            action = "ORG_WORKFLOW_DEFINITION_DELETE",
            actorId = null,
            context = context,
        )

        val def = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")
        checkWriteAccess(def, callerOrgId, isAppAdmin)

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
     * Copies a definition into the caller's org. Scrubs all [AssigneeSpec.Principal]
     * entries (hardcoded UUIDs) with ROLE placeholders to preserve portability.
     * The clone starts as inactive so the admin can review before enabling.
     * Sets [WorkflowDefinition.sourceTemplateId] to track provenance.
     */
    @Transactional
    fun cloneDefinition(
        id: UUID,
        callerOrgId: UUID,
        callerUserId: UUID,
        newName: String?,
    ): WorkflowDefinitionDto
    {
        val source = definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow definition not found: $id")

        if (!source.isTemplate && source.organizationId != callerOrgId)
        {
            throw ForbiddenException("Cannot clone a definition that belongs to another organization")
        }

        val scrubbedStepsJson = scrubPrincipalUuids(source.stepsJson)
        val clone = WorkflowDefinition().apply {
            name = uniqueCloneName(newName?.trim()?.ifBlank { null } ?: "${source.name} (copy)")
            summary = source.summary
            triggerEvent = source.triggerEvent
            stepsJson = scrubbedStepsJson
            generalTags = source.generalTags
            isActive = false
            scope = WorkflowScope.ORG
            organizationId = callerOrgId
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

    fun getInstanceDetail(id: UUID, callerOrgId: UUID?, isAppAdmin: Boolean): WorkflowInstanceDetailResponseDto
    {
        val instance = instanceRepository.findById(id)
            ?: throw IllegalArgumentException("Workflow instance not found: $id")

        if (!isAppAdmin && callerOrgId != null && instance.organizationId != null
            && instance.organizationId != callerOrgId)
        {
            throw ForbiddenException("Access denied to workflow instance $id")
        }

        val steps = stepRepository.findByInstance(instance.id)
        val defName = runCatching { definitionRepository.findById(instance.definitionId)?.name }.getOrNull()
        return instance.toDetailDto(defName, steps)
    }

    // -------------------------------------------------------------------------
    // Access control
    // -------------------------------------------------------------------------

    private fun checkReadAccess(def: WorkflowDefinition, callerOrgId: UUID?, isAppAdmin: Boolean, showUnpublished: Boolean = false)
    {
        if (isAppAdmin || def.isTemplate) return
        if (callerOrgId != null && def.organizationId == callerOrgId)
        {
            if (showUnpublished || def.isPublished) return
            throw ForbiddenException("Access denied to workflow definition ${def.id}")
        }
        throw ForbiddenException("Access denied to workflow definition ${def.id}")
    }

    private fun checkWriteAccess(def: WorkflowDefinition, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        if (isAppAdmin) return
        if (def.isTemplate) throw ForbiddenException("Platform templates cannot be modified directly; clone them instead")
        if (callerOrgId == null || def.organizationId != callerOrgId)
        {
            throw ForbiddenException("Access denied to workflow definition ${def.id}")
        }
    }

    // -------------------------------------------------------------------------
    // Clone name deduplication
    // -------------------------------------------------------------------------

    /**
     * Returns [base] if no definition with that name already exists at version 1,
     * otherwise appends " 2", " 3", … until a free slot is found.
     * Clones always start at version 1, so the uniqueness check is scoped to that version.
     */
    private fun uniqueCloneName(base: String): String
    {
        if (definitionRepository.findByNameAndVersion(base, 1) == null) return base
        var suffix = 2
        while (true)
        {
            val candidate = "$base $suffix"
            if (definitionRepository.findByNameAndVersion(candidate, 1) == null) return candidate
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

    // Private data classes that mirror the snapshot JSON shapes written by DefaultWorkflowEngineService.
    @Serializable
    private data class PrincipalRefEntry(val kind: String, val id: String)

    @Serializable
    private data class DecisionEntry(
        val principalKind: String,
        val principalId: String,
        val decision: String,
        val reason: String? = null,
        val atEpochMillis: Long,
    )

    @Serializable
    private data class SubjectFieldEntry(
        val name: String,
        val type: String,
        val description: String? = null,
        val lookupType: String? = null,
    )

    private fun decodeAssignees(jsonStr: String?): List<PrincipalRefEntry>
    {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(PrincipalRefEntry.serializer()), jsonStr)
        }.getOrDefault(emptyList())
    }

    private fun decodeDecisions(jsonStr: String): List<DecisionEntry> =
        runCatching {
            json.decodeFromString(ListSerializer(DecisionEntry.serializer()), jsonStr)
        }.getOrDefault(emptyList())

    private fun decodeSubjectFields(jsonStr: String): List<SubjectFieldEntry> =
        runCatching {
            json.decodeFromString(ListSerializer(SubjectFieldEntry.serializer()), jsonStr)
        }.getOrDefault(emptyList())

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
        assignees = decodeAssignees(assigneesSnapshotJson)
            .map { WorkflowPrincipalRefResponseDto(it.kind, it.id) },
        decisions = decodeDecisions(decisionsJson).map {
            WorkflowDecisionResponseDto(
                principalKind = it.principalKind,
                principalId = it.principalId,
                decision = it.decision,
                reason = it.reason,
                atEpochMillis = it.atEpochMillis,
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


