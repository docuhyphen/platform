package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.model.dto.BlueprintConfigJson
import com.docuhyphen.app.api.model.dto.BlueprintDefinitionDto
import com.docuhyphen.app.api.model.dto.BlueprintDocumentConfig
import com.docuhyphen.app.api.model.dto.BlueprintParticipantConfig
import com.docuhyphen.app.api.model.dto.CloneBlueprintRequest
import com.docuhyphen.app.api.model.dto.CreateBlueprintRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.model.entity.BlueprintDocumentDefault
import com.docuhyphen.app.api.model.entity.BlueprintParticipantDefault
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.repository.BlueprintDefinitionRepository
import com.docuhyphen.app.api.repository.BlueprintDocumentDefaultRepository
import com.docuhyphen.app.api.repository.BlueprintParticipantDefaultRepository
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class BlueprintDefinitionService @Inject constructor(
    private val repository: BlueprintDefinitionRepository,
    private val documentDefaultRepository: BlueprintDocumentDefaultRepository,
    private val participantDefaultRepository: BlueprintParticipantDefaultRepository,
)
{
    private val logger = LoggerFactory.getLogger(BlueprintDefinitionService::class.java)

    // encodeDefaults = true so the reconstructed config_json carries the same explicit
    // scalar fields the client sends, keeping the GET response byte-compatible.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun listBlueprints(
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
        scope: String?,
        tag: String?,
        isTemplate: Boolean?,
    ): List<BlueprintDefinitionDto>
    {
        return repository.findAllAccessibleForCaller(callerUserId, callerOrgId, isOrgAdmin, isAppAdmin)
            .asSequence()
            .filter { scope == null || it.scope.name == scope.uppercase() }
            .filter { tag == null || decodeTags(it.generalTags).contains(tag) }
            .filter { isTemplate == null || it.isTemplate == isTemplate }
            .map { it.toDto() }
            .toList()
    }

    fun getBlueprint(id: UUID, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean): BlueprintDefinitionDto
    {
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkReadAccess(bp, callerUserId, callerOrgId, isAppAdmin)
        return bp.toDto()
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    fun createBlueprint(
        request: CreateBlueprintRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): BlueprintDefinitionDto
    {
        val resolvedScope = resolveScope(request.scope, callerOrgId, isOrgAdmin, isAppAdmin)
        val bp = BlueprintDefinition().apply {
            name = request.name.trim()
            summary = request.summary?.trim()
            description = request.description?.trim()
            configJson = encodeConfig(parseConfig(request.configJson))
            generalTags = encodeTags(request.generalTags)
            isActive = request.isActive
            scope = resolvedScope
            organizationId = if (resolvedScope == BlueprintScope.PERSONAL) null else callerOrgId
            isTemplate = if (isAppAdmin && resolvedScope == BlueprintScope.APP) request.isTemplate else false
            createdByAppUserId = callerUserId
        }
        repository.save(bp)
        persistDocuments(bp.id, request.exchangeDocuments)
        persistParticipants(bp.id, request.participants)
        return bp.toDto()
    }

    @Transactional
    fun updateBlueprint(
        id: UUID,
        request: UpdateBlueprintRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): BlueprintDefinitionDto
    {
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkWriteAccess(bp, callerUserId, callerOrgId, isAppAdmin)

        request.name?.trim()?.let { if (it.isNotBlank()) bp.name = it }
        request.summary?.let { bp.summary = it.trim().ifBlank { null } }
        request.description?.let { bp.description = it.trim().ifBlank { null } }
        request.configJson?.let { bp.configJson = encodeConfig(parseConfig(it)) }
        request.exchangeDocuments?.let { persistDocuments(bp.id, it) }
        request.participants?.let { persistParticipants(bp.id, it) }
        request.generalTags?.let { bp.generalTags = encodeTags(it) }
        bp.updatedAt = Timestamp.from(Instant.now())

        return repository.update(bp).toDto()
    }

    @Transactional
    fun patchPublished(
        id: UUID,
        request: PatchBlueprintPublishedRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): BlueprintDefinitionDto
    {
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkWriteAccess(bp, callerUserId, callerOrgId, isAppAdmin)
        if (bp.scope == BlueprintScope.PERSONAL)
        {
            throw ForbiddenException("Personal blueprints cannot be published")
        }
        bp.isPublished = request.isPublished
        bp.updatedAt = Timestamp.from(Instant.now())
        return repository.update(bp).toDto()
    }

    @Transactional
    fun patchStatus(
        id: UUID,
        request: PatchBlueprintStatusRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): BlueprintDefinitionDto
    {
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkWriteAccess(bp, callerUserId, callerOrgId, isAppAdmin)
        bp.isActive = request.isActive
        bp.updatedAt = Timestamp.from(Instant.now())
        return repository.update(bp).toDto()
    }

    @Transactional
    fun deleteBlueprint(id: UUID, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkWriteAccess(bp, callerUserId, callerOrgId, isAppAdmin)
        bp.isDeleted = true
        bp.isActive = false
        bp.updatedAt = Timestamp.from(Instant.now())
        repository.update(bp)
        logger.info("Blueprint {} soft-deleted by user {}", id, callerUserId)
    }

    @Transactional
    fun cloneBlueprint(
        id: UUID,
        request: CloneBlueprintRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): BlueprintDefinitionDto
    {
        val source = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkReadAccess(source, callerUserId, callerOrgId, isAppAdmin)

        val clone = BlueprintDefinition().apply {
            name = request.newName?.trim()?.ifBlank { null } ?: "${source.name} (copy)"
            summary = source.summary
            description = source.description
            configJson = source.configJson
            generalTags = source.generalTags
            isActive = false
            scope = BlueprintScope.PERSONAL
            organizationId = null
            isTemplate = false
            sourceTemplateId = source.id
            createdByAppUserId = callerUserId
        }
        repository.save(clone)
        copyChildren(source.id, clone.id)
        return clone.toDto()
    }

    // ── Access control ────────────────────────────────────────────────────────

    private fun checkReadAccess(bp: BlueprintDefinition, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        if (isAppAdmin) return
        when (bp.scope)
        {
            BlueprintScope.PERSONAL ->
            {
                if (bp.createdByAppUserId != callerUserId)
                    throw ForbiddenException("Access denied to blueprint ${bp.id}")
            }
            BlueprintScope.ORG ->
            {
                if (callerOrgId == null || bp.organizationId != callerOrgId)
                    throw ForbiddenException("Access denied to blueprint ${bp.id}")
            }
            BlueprintScope.APP -> { /* public */ }
        }
    }

    private fun checkWriteAccess(bp: BlueprintDefinition, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        if (isAppAdmin) return
        when (bp.scope)
        {
            BlueprintScope.PERSONAL ->
            {
                if (bp.createdByAppUserId != callerUserId)
                    throw ForbiddenException("Access denied to blueprint ${bp.id}")
            }
            BlueprintScope.ORG ->
            {
                if (callerOrgId == null || bp.organizationId != callerOrgId)
                    throw ForbiddenException("Access denied to blueprint ${bp.id}")
            }
            BlueprintScope.APP ->
                throw ForbiddenException("Platform blueprints cannot be modified directly; clone them instead")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveScope(
        requestedScope: String?,
        callerOrgId: UUID?,
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
            if (parsed == BlueprintScope.ORG && !isOrgAdmin && !isAppAdmin)
                throw ForbiddenException("Org admin role required to create ORG-scoped blueprints")
            return parsed
        }
        return when
        {
            isAppAdmin -> BlueprintScope.APP
            isOrgAdmin && callerOrgId != null -> BlueprintScope.ORG
            else -> BlueprintScope.PERSONAL
        }
    }

    private fun parseConfig(configJson: String): BlueprintConfigJson =
        runCatching { json.decodeFromString(BlueprintConfigJson.serializer(), configJson) }
            .getOrElse { throw IllegalArgumentException("Invalid configJson: ${it.message}") }

    private fun encodeConfig(config: BlueprintConfigJson): String =
        json.encodeToString(BlueprintConfigJson.serializer(), config)

    /** Deletes and re-inserts the document default rows for [blueprintId]. */
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

    /** Deletes and re-inserts the participant default rows for [blueprintId]. */
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

    /** Copies the child rows from [sourceId] onto [targetId] (used by clone). */
    private fun copyChildren(sourceId: UUID, targetId: UUID)
    {
        documentDefaultRepository.findAllByBlueprintDefinitionId(sourceId).forEach { src ->
            documentDefaultRepository.save(
                BlueprintDocumentDefault().apply {
                    this.blueprintDefinitionId = targetId
                    this.title = src.title
                    this.restrictedType = src.restrictedType
                    this.restrictType = src.restrictType
                    this.required = src.required
                    this.libraryDocumentId = src.libraryDocumentId
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
        exchangeDocuments = loadDocuments(id),
        participants = loadParticipants(id),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
