package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.model.dto.BlueprintConfigJson
import com.docuhyphen.app.api.model.dto.BlueprintDefinitionDto
import com.docuhyphen.app.api.model.dto.BlueprintDocumentConfig
import com.docuhyphen.app.api.model.dto.BlueprintFieldDefaultConfig
import com.docuhyphen.app.api.model.dto.BlueprintParticipantConfig
import com.docuhyphen.app.api.model.dto.CloneBlueprintRequest
import com.docuhyphen.app.api.model.dto.CreateBlueprintRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.model.entity.BlueprintDocumentDefault
import com.docuhyphen.app.api.model.entity.BlueprintFieldDefault
import com.docuhyphen.app.api.model.entity.BlueprintParticipantDefault
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.model.entity.DocumentLibraryEntry
import com.docuhyphen.app.api.repository.BlueprintDefinitionRepository
import com.docuhyphen.app.api.repository.BlueprintDocumentDefaultRepository
import com.docuhyphen.app.api.repository.BlueprintFieldDefaultRepository
import com.docuhyphen.app.api.repository.BlueprintParticipantDefaultRepository
import com.docuhyphen.app.api.repository.DocumentLibraryRepository
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class BlueprintDefinitionService @Inject constructor(
    private val repository: BlueprintDefinitionRepository,
    private val documentDefaultRepository: BlueprintDocumentDefaultRepository,
    private val participantDefaultRepository: BlueprintParticipantDefaultRepository,
    private val fieldDefaultRepository: BlueprintFieldDefaultRepository,
    private val documentLibraryRepository: DocumentLibraryRepository,
    private val schemaDefinitionService: SchemaDefinitionService,
    private val adminActionGuardService: AdminActionGuardService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val userRoleService: UserRoleService,
    private val blueprintSubscriptionGuard: BlueprintSubscriptionGuard,
)
{
    private val logger = LoggerFactory.getLogger(BlueprintDefinitionService::class.java)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun listBlueprints(scope: String?, tag: String?, isTemplate: Boolean?): List<BlueprintDefinitionDto>
    {
        val principal = currentPrincipal()
        val activeOrgId = currentContext().activeOrgId
        blueprintSubscriptionGuard.requireBlueprintUse(principal.id, activeOrgId)
        val isOrgAdmin = activeOrgId != null && userRoleService.isOrgAdminIn(principal.id, activeOrgId)

        return repository.findAllAccessibleForCaller(principal.id, activeOrgId, isOrgAdmin)
            .asSequence()
            .filter { scope == null || it.scope.name == scope.uppercase() }
            .filter { tag == null || decodeTags(it.generalTags).contains(tag) }
            .filter { isTemplate == null || it.isTemplate == isTemplate }
            .map { it.toDto() }
            .toList()
    }

    fun getBlueprint(id: UUID): BlueprintDefinitionDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        blueprintSubscriptionGuard.requireBlueprintUse(principal.id, context.activeOrgId)
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkReadAccess(bp, principal, context)
        return bp.toDto()
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    fun createBlueprint(request: CreateBlueprintRequest, context: AdminApprovalContext): BlueprintDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val activeOrgId = authContext.activeOrgId
        val isOrgAdmin = activeOrgId != null && userRoleService.isOrgAdminIn(principal.id, activeOrgId)
        val isAppAdmin = userRoleService.isAppAdmin(principal.id)

        val resolvedScope = resolveScope(request.scope, activeOrgId, isOrgAdmin, isAppAdmin)
        blueprintSubscriptionGuard.requireBlueprintManagement(principal.id, resolvedScope, activeOrgId)
        adminActionGuardService.enforce(
            action = actionFor(resolvedScope, "CREATE"),
            actorId = principal.id,
            context = context,
        )
        val bp = BlueprintDefinition().apply {
            name = request.name.trim()
            summary = request.summary?.trim()
            description = request.description?.trim()
            configJson = encodeConfig(parseConfig(request.configJson))
            schemaDefinitionId = request.schemaDefinitionId
            generalTags = encodeTags(request.generalTags)
            isActive = request.isActive
            scope = resolvedScope
            organizationId = if (resolvedScope == BlueprintScope.ORG) activeOrgId else null
            isTemplate = if (isAppAdmin && resolvedScope == BlueprintScope.APP) request.isTemplate else false
            createdByAppUserId = principal.id
        }
        repository.save(bp)
        persistDocuments(bp.id, request.exchangeDocuments)
        persistParticipants(bp.id, request.participants)
        persistFieldDefaults(bp.id, request.schemaDefinitionId, request.fieldDefaults)
        return bp.toDto()
    }

    @Transactional
    fun updateBlueprint(id: UUID, request: UpdateBlueprintRequest, context: AdminApprovalContext): BlueprintDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkWriteAccess(bp, principal, authContext)
        blueprintSubscriptionGuard.requireBlueprintManagement(principal.id, bp.scope, bp.organizationId)
        adminActionGuardService.enforce(
            action = actionFor(bp.scope, "UPDATE"),
            actorId = principal.id,
            context = context,
        )

        request.name?.trim()?.let { if (it.isNotBlank()) bp.name = it }
        request.summary?.let { bp.summary = it.trim().ifBlank { null } }
        request.description?.let { bp.description = it.trim().ifBlank { null } }
        request.configJson?.let { bp.configJson = encodeConfig(parseConfig(it)) }
        request.exchangeDocuments?.let { persistDocuments(bp.id, it) }
        request.participants?.let { persistParticipants(bp.id, it) }
        request.fieldDefaults?.let {
            bp.schemaDefinitionId = request.schemaDefinitionId
            persistFieldDefaults(bp.id, request.schemaDefinitionId, it)
        }
        request.generalTags?.let { bp.generalTags = encodeTags(it) }
        bp.updatedAt = Timestamp.from(Instant.now())

        return repository.update(bp).toDto()
    }

    @Transactional
    fun patchPublished(id: UUID, request: PatchBlueprintPublishedRequest, context: AdminApprovalContext): BlueprintDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkWriteAccess(bp, principal, authContext)
        if (bp.scope == BlueprintScope.PERSONAL)
        {
            throw ForbiddenException("Personal blueprints cannot be published")
        }
        blueprintSubscriptionGuard.requireBlueprintManagement(principal.id, bp.scope, bp.organizationId)
        adminActionGuardService.enforce(
            action = actionFor(bp.scope, "PUBLISH_UPDATE"),
            actorId = principal.id,
            context = context,
        )
        bp.isPublished = request.isPublished
        bp.updatedAt = Timestamp.from(Instant.now())
        return repository.update(bp).toDto()
    }

    @Transactional
    fun patchStatus(id: UUID, request: PatchBlueprintStatusRequest, context: AdminApprovalContext): BlueprintDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkWriteAccess(bp, principal, authContext)
        blueprintSubscriptionGuard.requireBlueprintManagement(principal.id, bp.scope, bp.organizationId)
        adminActionGuardService.enforce(
            action = actionFor(bp.scope, "STATUS_UPDATE"),
            actorId = principal.id,
            context = context,
        )
        bp.isActive = request.isActive
        bp.updatedAt = Timestamp.from(Instant.now())
        return repository.update(bp).toDto()
    }

    @Transactional
    fun deleteBlueprint(id: UUID, context: AdminApprovalContext)
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkWriteAccess(bp, principal, authContext)
        blueprintSubscriptionGuard.requireBlueprintManagement(principal.id, bp.scope, bp.organizationId)
        adminActionGuardService.enforce(
            action = actionFor(bp.scope, "DELETE"),
            actorId = principal.id,
            context = context,
        )
        bp.isDeleted = true
        bp.isActive = false
        bp.updatedAt = Timestamp.from(Instant.now())
        repository.update(bp)
        logger.info("Blueprint {} soft-deleted by user {}", id, principal.id)
    }

    @Transactional
    fun cloneBlueprint(id: UUID, request: CloneBlueprintRequest, context: AdminApprovalContext): BlueprintDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val activeOrgId = authContext.activeOrgId
        val isOrgAdmin = activeOrgId != null && userRoleService.isOrgAdminIn(principal.id, activeOrgId)

        val source = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkReadAccess(source, principal, authContext)

        val targetScope = resolveCloneTargetScope(request.targetScope, activeOrgId, isOrgAdmin)
        blueprintSubscriptionGuard.requireBlueprintManagement(principal.id, targetScope, activeOrgId)
        adminActionGuardService.enforce(
            action = actionFor(targetScope, "CLONE"),
            actorId = principal.id,
            context = context,
        )
        val clone = BlueprintDefinition().apply {
            name = request.newName?.trim()?.ifBlank { null } ?: "${source.name} (copy)"
            summary = source.summary
            description = source.description
            configJson = source.configJson
            schemaDefinitionId = source.schemaDefinitionId
            generalTags = source.generalTags
            isActive = false
            scope = targetScope
            organizationId = if (targetScope == BlueprintScope.ORG) activeOrgId else null
            isTemplate = false
            sourceTemplateId = source.id
            createdByAppUserId = principal.id
        }
        repository.save(clone)
        copyChildren(source.id, clone.id, targetScope, principal.id, activeOrgId)
        return clone.toDto()
    }

    // ── Access control ────────────────────────────────────────────────────────

    private fun checkReadAccess(bp: BlueprintDefinition, principal: PrincipalRef, context: AuthorizationContext)
    {
        when (bp.scope)
        {
            BlueprintScope.PERSONAL ->
                if (bp.createdByAppUserId != principal.id)
                    throw ForbiddenException("Access denied to blueprint ${bp.id}")
            BlueprintScope.ORG ->
            {
                val decision = authorizationService.authorize(
                    principal, Action.BLUEPRINT_VIEW, ResourceRef.blueprint(bp.id), context,
                )
                if (decision is Decision.Deny)
                    throw ForbiddenException("Access denied to blueprint ${bp.id}")
            }
            BlueprintScope.APP -> { }
        }
    }

    private fun checkWriteAccess(bp: BlueprintDefinition, principal: PrincipalRef, context: AuthorizationContext)
    {
        when (bp.scope)
        {
            BlueprintScope.PERSONAL ->
                if (bp.createdByAppUserId != principal.id)
                    throw ForbiddenException("Access denied to blueprint ${bp.id}")
            BlueprintScope.ORG ->
            {
                val decision = authorizationService.authorize(
                    principal, Action.BLUEPRINT_EDIT, ResourceRef.blueprint(bp.id), context,
                )
                if (decision is Decision.Deny)
                    throw ForbiddenException("Access denied to blueprint ${bp.id}")
            }
            BlueprintScope.APP ->
                if (!userRoleService.isAppAdmin(principal.id))
                    throw ForbiddenException("App admin role required to modify APP-scoped blueprints")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun currentPrincipal(): PrincipalRef =
        authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Not authenticated")

    private fun currentContext(): AuthorizationContext =
        authorizationContextFactory.currentContext()

    private fun resolveScope(
        requestedScope: String?,
        activeOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): BlueprintScope
    {
        if (requestedScope != null)
        {
            val parsed = runCatching { BlueprintScope.valueOf(requestedScope.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid scope: $requestedScope") }
            if (parsed == BlueprintScope.APP && !isAppAdmin)
                throw ForbiddenException("App admin role required to create APP-scoped blueprints")
            if (parsed == BlueprintScope.ORG && !isOrgAdmin)
                throw ForbiddenException("Org admin role required to create ORG-scoped blueprints")
            return parsed
        }
        return when
        {
            isOrgAdmin && activeOrgId != null -> BlueprintScope.ORG
            isAppAdmin -> BlueprintScope.APP
            else -> BlueprintScope.PERSONAL
        }
    }

    private fun resolveCloneTargetScope(
        requested: String?,
        activeOrgId: UUID?,
        isOrgAdmin: Boolean,
    ): BlueprintScope
    {
        if (requested == null) return BlueprintScope.PERSONAL
        return when (requested.uppercase())
        {
            "PERSONAL" -> BlueprintScope.PERSONAL
            "ORG" ->
            {
                if (!isOrgAdmin)
                    throw ForbiddenException("Org admin role required to clone into the organization collection")
                if (activeOrgId == null)
                    throw ForbiddenException("No organization membership found")
                BlueprintScope.ORG
            }
            else -> throw ForbiddenException("Cannot clone directly into scope: $requested")
        }
    }

    private fun actionFor(scope: BlueprintScope, operation: String): String =
        when (scope)
        {
            BlueprintScope.PERSONAL -> "PERSONAL_BLUEPRINT_$operation"
            BlueprintScope.ORG -> "ORG_BLUEPRINT_$operation"
            BlueprintScope.APP -> "APP_BLUEPRINT_$operation"
        }

    private fun parseConfig(configJson: String): BlueprintConfigJson =
        runCatching { json.decodeFromString(BlueprintConfigJson.serializer(), configJson) }
            .getOrElse { throw IllegalArgumentException("Invalid configJson: ${it.message}") }

    private fun encodeConfig(config: BlueprintConfigJson): String =
        json.encodeToString(BlueprintConfigJson.serializer(), config)

    private fun persistDocuments(blueprintId: UUID, docs: List<BlueprintDocumentConfig>)
    {
        documentDefaultRepository.deleteAllByBlueprintDefinitionId(blueprintId)
        docs.forEachIndexed { idx, doc ->
            documentDefaultRepository.save(
                BlueprintDocumentDefault().apply {
                    this.blueprintDefinitionId = blueprintId
                    this.title = doc.title
                    this.restrictedType = doc.restrictedType
                    this.restrictType = doc.restrictType
                    this.required = doc.required
                    this.libraryDocumentId = doc.libraryDocumentId
                    this.displayOrder = idx
                }
            )
        }
    }

    private fun persistParticipants(blueprintId: UUID, participants: List<BlueprintParticipantConfig>)
    {
        participantDefaultRepository.deleteAllByBlueprintDefinitionId(blueprintId)
        participants.forEachIndexed { idx, p ->
            participantDefaultRepository.save(
                BlueprintParticipantDefault().apply {
                    this.blueprintDefinitionId = blueprintId
                    this.principalKind = p.principalKind
                    this.principalId = p.principalId
                    this.roleName = p.roleName
                    this.displayOrder = idx
                }
            )
        }
    }

    private fun copyChildren(
        sourceId: UUID,
        targetId: UUID,
        targetScope: BlueprintScope,
        callerUserId: UUID,
        callerOrgId: UUID?,
    )
    {
        documentDefaultRepository.findAllByBlueprintDefinitionId(sourceId).forEach { src ->
            val resolvedLibraryDocumentId = src.libraryDocumentId?.let { libId ->
                val libEntry = documentLibraryRepository.findById(libId)
                if (libEntry != null && libEntry.scope == BlueprintScope.APP)
                {
                    documentLibraryRepository.save(
                        DocumentLibraryEntry().apply {
                            title = libEntry.title
                            description = libEntry.description
                            generalTags = libEntry.generalTags
                            documentType = libEntry.documentType
                            fileName = libEntry.fileName
                            fileSizeBytes = libEntry.fileSizeBytes
                            storagePath = libEntry.storagePath
                            contentHash = libEntry.contentHash
                            isActive = libEntry.isActive
                            isPublished = false
                            scope = targetScope
                            organizationId = if (targetScope == BlueprintScope.ORG) callerOrgId else null
                            sourceDocumentId = libEntry.id
                            createdByAppUserId = callerUserId
                        }
                    ).id
                }
                else libId
            }
            documentDefaultRepository.save(
                BlueprintDocumentDefault().apply {
                    this.blueprintDefinitionId = targetId
                    this.title = src.title
                    this.restrictedType = src.restrictedType
                    this.restrictType = src.restrictType
                    this.required = src.required
                    this.libraryDocumentId = resolvedLibraryDocumentId
                    this.displayOrder = src.displayOrder
                }
            )
        }
        participantDefaultRepository.findAllByBlueprintDefinitionId(sourceId).forEach { src ->
            participantDefaultRepository.save(
                BlueprintParticipantDefault().apply {
                    this.blueprintDefinitionId = targetId
                    this.principalKind = src.principalKind
                    this.principalId = src.principalId
                    this.roleName = src.roleName
                    this.displayOrder = src.displayOrder
                }
            )
        }
        fieldDefaultRepository.findAllByBlueprintDefinitionId(sourceId).forEach { src ->
            fieldDefaultRepository.save(
                BlueprintFieldDefault().apply {
                    this.blueprintDefinitionId = targetId
                    this.fieldDefinitionId = src.fieldDefinitionId
                    this.valueType = src.valueType
                    this.valueJson = src.valueJson
                    this.displayOrder = src.displayOrder
                }
            )
        }
    }

    private fun loadDocuments(blueprintId: UUID): List<BlueprintDocumentConfig> =
        documentDefaultRepository.findAllByBlueprintDefinitionId(blueprintId).map {
            BlueprintDocumentConfig(
                title = it.title,
                restrictedType = it.restrictedType,
                restrictType = it.restrictType,
                required = it.required,
                libraryDocumentId = it.libraryDocumentId,
            )
        }

    private fun loadParticipants(blueprintId: UUID): List<BlueprintParticipantConfig> =
        participantDefaultRepository.findAllByBlueprintDefinitionId(blueprintId).map {
            BlueprintParticipantConfig(
                principalId = it.principalId,
                principalKind = it.principalKind,
                roleName = it.roleName,
            )
        }

    /**
     * Validates (against the schema's published version) and replaces a blueprint's default field
     * values. Empty defaults clear the rows. Non-empty defaults require a schema; each value is
     * canonicalized through its type contract so an invalid default is rejected at authoring time.
     */
    private fun persistFieldDefaults(
        blueprintId: UUID,
        schemaDefinitionId: UUID?,
        defaults: List<BlueprintFieldDefaultConfig>,
    )
    {
        fieldDefaultRepository.deleteAllByBlueprintDefinitionId(blueprintId)
        val nonEmpty = defaults.filter { it.value != null && it.value !is JsonNull }
        if (nonEmpty.isEmpty()) return
        if (schemaDefinitionId == null)
            throw IllegalArgumentException("Field defaults were supplied without a schema; select a schema first")

        val validated = schemaDefinitionService.validateDefaultsForSchema(
            schemaDefinitionId,
            nonEmpty.map { it.fieldDefinitionId to it.value!! },
        ).associateBy { it.fieldDefinitionId }

        nonEmpty.forEachIndexed { idx, default ->
            val resolved = validated[default.fieldDefinitionId] ?: return@forEachIndexed
            fieldDefaultRepository.save(
                BlueprintFieldDefault().apply {
                    this.blueprintDefinitionId = blueprintId
                    this.fieldDefinitionId = default.fieldDefinitionId
                    this.valueType = resolved.valueType
                    this.valueJson = json.encodeToString(JsonElement.serializer(), default.value!!)
                    this.displayOrder = idx
                }
            )
        }
    }

    private fun loadFieldDefaults(blueprintId: UUID): List<BlueprintFieldDefaultConfig> =
        fieldDefaultRepository.findAllByBlueprintDefinitionId(blueprintId).map {
            BlueprintFieldDefaultConfig(
                fieldDefinitionId = it.fieldDefinitionId,
                valueType = it.valueType,
                value = it.valueJson?.let { raw -> json.parseToJsonElement(raw) },
                displayOrder = it.displayOrder,
            )
        }

    private fun decodeTags(tagsJson: String): List<String> =
        runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), tagsJson)
        }.getOrDefault(emptyList())

    private fun encodeTags(tags: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), tags)

    private fun BlueprintDefinition.toDto() = BlueprintDefinitionDto(
        id = id,
        name = name,
        summary = summary,
        description = description,
        scope = scope.name,
        organizationId = organizationId,
        createdByAppUserId = createdByAppUserId,
        isActive = isActive,
        isPublished = isPublished,
        isTemplate = isTemplate,
        generalTags = decodeTags(generalTags),
        sourceTemplateId = sourceTemplateId,
        configJson = configJson,
        schemaDefinitionId = schemaDefinitionId,
        exchangeDocuments = loadDocuments(id),
        participants = loadParticipants(id),
        fieldDefaults = loadFieldDefaults(id),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
