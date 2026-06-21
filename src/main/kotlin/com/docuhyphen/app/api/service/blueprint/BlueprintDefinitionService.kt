package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.model.dto.BlueprintConfigJson
import com.docuhyphen.app.api.model.dto.BlueprintDefinitionDto
import com.docuhyphen.app.api.model.dto.CloneBlueprintRequest
import com.docuhyphen.app.api.model.dto.CreateBlueprintRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.repository.BlueprintDefinitionRepository
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
)
{
    private val logger = LoggerFactory.getLogger(BlueprintDefinitionService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

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
        validateConfigJson(request.configJson)
        val resolvedScope = resolveScope(request.scope, callerOrgId, isOrgAdmin, isAppAdmin)
        val bp = BlueprintDefinition().apply {
            name = request.name.trim()
            summary = request.summary?.trim()
            description = request.description?.trim()
            configJson = request.configJson
            generalTags = encodeTags(request.generalTags)
            isActive = request.isActive
            scope = resolvedScope
            organizationId = if (resolvedScope == BlueprintScope.PERSONAL) null else callerOrgId
            isTemplate = if (isAppAdmin && resolvedScope == BlueprintScope.APP) request.isTemplate else false
            createdByAppUserId = callerUserId
        }
        return repository.save(bp).toDto()
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
        request.configJson?.let {
            validateConfigJson(it)
            bp.configJson = it
        }
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
        return repository.save(clone).toDto()
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

    private fun validateConfigJson(configJson: String)
    {
        runCatching { json.decodeFromString(BlueprintConfigJson.serializer(), configJson) }
            .getOrElse { throw IllegalArgumentException("Invalid configJson: ${it.message}") }
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
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
