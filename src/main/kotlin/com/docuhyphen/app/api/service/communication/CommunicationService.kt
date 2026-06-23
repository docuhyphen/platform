package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.model.dto.CloneCommunicationRequest
import com.docuhyphen.app.api.model.dto.CreateCommunicationRequest
import com.docuhyphen.app.api.model.dto.CommunicationDto
import com.docuhyphen.app.api.model.dto.PatchCommunicationPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchCommunicationStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateCommunicationRequest
import com.docuhyphen.app.api.model.entity.Communication
import com.docuhyphen.app.api.model.entity.CommunicationScope
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.CommunicationRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.variable.TemplateVariableInterpolator
import com.docuhyphen.app.api.service.variable.VariableResolutionContext
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
class CommunicationService @Inject constructor(
    private val repository: CommunicationRepository,
    private val appUserRepository: AppUserRepository,
    private val organizationRepository: OrganizationRepository,
    private val interpolator: TemplateVariableInterpolator,
)
{
    private val logger = LoggerFactory.getLogger(CommunicationService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun listTemplates(
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
        scope: String?,
        tag: String?,
        isTemplate: Boolean?,
    ): List<CommunicationDto>
    {
        return repository.findAllAccessibleForCaller(callerUserId, callerOrgId, isOrgAdmin, isAppAdmin)
            .asSequence()
            .filter { scope == null || it.scope.name == scope.uppercase() }
            .filter { tag == null || decodeTags(it.generalTags).contains(tag) }
            .filter { isTemplate == null || it.isTemplate == isTemplate }
            .map { it.toDto() }
            .toList()
    }

    fun getTemplate(id: UUID, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean): CommunicationDto
    {
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkReadAccess(communication, callerUserId, callerOrgId, isAppAdmin)
        return communication.toDto()
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    fun createTemplate(
        request: CreateCommunicationRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): CommunicationDto
    {
        if (request.subject.isBlank()) throw IllegalArgumentException("subject is required")
        if (request.body.isBlank()) throw IllegalArgumentException("body is required")

        val resolvedScope = resolveScope(request.scope, callerOrgId, isOrgAdmin, isAppAdmin)
        val communication = Communication().apply {
            name = request.name.trim()
            subject = request.subject.trim()
            body = request.body
            summary = request.summary?.trim()
            description = request.description?.trim()
            generalTags = encodeTags(request.generalTags)
            isActive = request.isActive
            scope = resolvedScope
            organizationId = if (resolvedScope == CommunicationScope.PERSONAL) null else callerOrgId
            isTemplate = if (isAppAdmin && resolvedScope == CommunicationScope.PLATFORM) request.isTemplate else false
            createdByAppUserId = callerUserId
        }
        return repository.save(communication).toDto()
    }

    @Transactional
    fun updateTemplate(
        id: UUID,
        request: UpdateCommunicationRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): CommunicationDto
    {
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkWriteAccess(communication, callerUserId, callerOrgId, isAppAdmin)

        request.name?.trim()?.let { if (it.isNotBlank()) communication.name = it }
        request.subject?.trim()?.let { if (it.isNotBlank()) communication.subject = it }
        request.body?.let { if (it.isNotBlank()) communication.body = it }
        request.summary?.let { communication.summary = it.trim().ifBlank { null } }
        request.description?.let { communication.description = it.trim().ifBlank { null } }
        request.generalTags?.let { communication.generalTags = encodeTags(it) }
        communication.updatedAt = Timestamp.from(Instant.now())

        return repository.update(communication).toDto()
    }

    @Transactional
    fun patchPublished(
        id: UUID,
        request: PatchCommunicationPublishedRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): CommunicationDto
    {
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkWriteAccess(communication, callerUserId, callerOrgId, isAppAdmin)
        if (communication.scope == CommunicationScope.PERSONAL)
        {
            throw ForbiddenException("Personal communications cannot be published")
        }
        communication.isPublished = request.isPublished
        communication.updatedAt = Timestamp.from(Instant.now())
        return repository.update(communication).toDto()
    }

    @Transactional
    fun patchStatus(
        id: UUID,
        request: PatchCommunicationStatusRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): CommunicationDto
    {
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkWriteAccess(communication, callerUserId, callerOrgId, isAppAdmin)
        communication.isActive = request.isActive
        communication.updatedAt = Timestamp.from(Instant.now())
        return repository.update(communication).toDto()
    }

    @Transactional
    fun deleteTemplate(id: UUID, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkWriteAccess(communication, callerUserId, callerOrgId, isAppAdmin)
        communication.isDeleted = true
        communication.isActive = false
        communication.updatedAt = Timestamp.from(Instant.now())
        repository.update(communication)
        logger.info("Communication {} soft-deleted by user {}", id, callerUserId)
    }

    @Transactional
    fun cloneTemplate(
        id: UUID,
        request: CloneCommunicationRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): CommunicationDto
    {
        val source = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkReadAccess(source, callerUserId, callerOrgId, isAppAdmin)

        val clone = Communication().apply {
            name = request.newName?.trim()?.ifBlank { null } ?: "${source.name} (copy)"
            subject = source.subject
            body = source.body
            summary = source.summary
            description = source.description
            generalTags = source.generalTags
            isActive = false
            scope = CommunicationScope.PERSONAL
            organizationId = null
            isTemplate = false
            sourceTemplateId = source.id
            createdByAppUserId = callerUserId
        }
        return repository.save(clone).toDto()
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    fun preview(
        id: UUID,
        sampleVariables: Map<String, String>,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): RenderedCommunication
    {
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkReadAccess(communication, callerUserId, callerOrgId, isAppAdmin)

        val user = appUserRepository.findById(callerUserId)
            ?: throw IllegalArgumentException("User not found: $callerUserId")
        val org = callerOrgId?.let { organizationRepository.findById(it) }

        val ctx = VariableResolutionContext(
            user = user,
            organization = org,
            timestamp = Instant.now(),
            overrides = sampleVariables,
        )

        val hasSeqTokens = communication.subject.contains("{{SEQ:") || communication.body.contains("{{SEQ:")

        val resolvedSubject: String
        val resolvedBody: String
        if (hasSeqTokens)
        {
            resolvedSubject = interpolator.interpolateWithSequences(communication.subject, ctx).resolved
            resolvedBody = interpolator.interpolateWithSequences(communication.body, ctx).resolved
        }
        else
        {
            resolvedSubject = interpolator.interpolate(communication.subject, ctx).resolved
            resolvedBody = interpolator.interpolate(communication.body, ctx).resolved
        }

        return RenderedCommunication(subject = resolvedSubject, body = resolvedBody)
    }

    // ── Access control ────────────────────────────────────────────────────────

    private fun checkReadAccess(communication: Communication, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        if (isAppAdmin) return
        when (communication.scope)
        {
            CommunicationScope.PERSONAL ->
            {
                if (communication.createdByAppUserId != callerUserId)
                    throw ForbiddenException("Access denied to communication ${communication.id}")
            }
            CommunicationScope.ORG ->
            {
                if (callerOrgId == null || communication.organizationId != callerOrgId)
                    throw ForbiddenException("Access denied to communication ${communication.id}")
            }
            CommunicationScope.PLATFORM -> { /* public read */ }
        }
    }

    private fun checkWriteAccess(communication: Communication, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        if (isAppAdmin) return
        when (communication.scope)
        {
            CommunicationScope.PERSONAL ->
            {
                if (communication.createdByAppUserId != callerUserId)
                    throw ForbiddenException("Access denied to communication ${communication.id}")
            }
            CommunicationScope.ORG ->
            {
                if (callerOrgId == null || communication.organizationId != callerOrgId)
                    throw ForbiddenException("Access denied to communication ${communication.id}")
            }
            CommunicationScope.PLATFORM ->
                throw ForbiddenException("Platform communications cannot be modified directly; clone them instead")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveScope(
        requestedScope: String?,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): CommunicationScope
    {
        if (requestedScope != null)
        {
            val parsed = runCatching { CommunicationScope.valueOf(requestedScope.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid scope: $requestedScope") }
            if (parsed == CommunicationScope.PLATFORM && !isAppAdmin)
                throw ForbiddenException("App admin role required to create PLATFORM-scoped communications")
            if (parsed == CommunicationScope.ORG && !isOrgAdmin && !isAppAdmin)
                throw ForbiddenException("Org admin role required to create ORG-scoped communications")
            return parsed
        }
        return when
        {
            isAppAdmin -> CommunicationScope.PLATFORM
            isOrgAdmin && callerOrgId != null -> CommunicationScope.ORG
            else -> CommunicationScope.PERSONAL
        }
    }

    private fun decodeTags(tagsJson: String): List<String> =
        runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), tagsJson)
        }.getOrDefault(emptyList())

    private fun encodeTags(tags: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), tags)

    private fun Communication.toDto() = CommunicationDto(
        id = id,
        name = name,
        summary = summary,
        description = description,
        scope = scope.name,
        organizationId = organizationId,
        createdByAppUserId = createdByAppUserId,
        subject = subject,
        body = body,
        generalTags = decodeTags(generalTags),
        isActive = isActive,
        isPublished = isPublished,
        isTemplate = isTemplate,
        sourceTemplateId = sourceTemplateId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
