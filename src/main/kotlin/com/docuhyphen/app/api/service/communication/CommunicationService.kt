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
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
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
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val userRoleService: UserRoleService,
)
{
    private val logger = LoggerFactory.getLogger(CommunicationService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun listTemplates(scope: String?, tag: String?, isTemplate: Boolean?): List<CommunicationDto>
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val activeOrgId = context.activeOrgId
        val isOrgAdmin = activeOrgId != null && userRoleService.isOrgAdminIn(principal.id, activeOrgId)

        return repository.findAllAccessibleForCaller(principal.id, activeOrgId, isOrgAdmin)
            .asSequence()
            .filter { scope == null || it.scope.name == scope.uppercase() }
            .filter { tag == null || decodeTags(it.generalTags).contains(tag) }
            .filter { isTemplate == null || it.isTemplate == isTemplate }
            .map { it.toDto() }
            .toList()
    }

    fun getTemplate(id: UUID): CommunicationDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkReadAccess(communication, principal, context)
        return communication.toDto()
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    fun createTemplate(request: CreateCommunicationRequest): CommunicationDto
    {
        if (request.subject.isBlank()) throw IllegalArgumentException("subject is required")
        if (request.body.isBlank()) throw IllegalArgumentException("body is required")

        val principal = currentPrincipal()
        val context = currentContext()
        val activeOrgId = context.activeOrgId
        val isOrgAdmin = activeOrgId != null && userRoleService.isOrgAdminIn(principal.id, activeOrgId)
        val isAppAdmin = userRoleService.isAppAdmin(principal.id)

        val resolvedScope = resolveScope(request.scope, activeOrgId, isOrgAdmin, isAppAdmin)
        val communication = Communication().apply {
            name = request.name.trim()
            subject = request.subject.trim()
            body = request.body
            summary = request.summary?.trim()
            description = request.description?.trim()
            generalTags = encodeTags(request.generalTags)
            isActive = request.isActive
            scope = resolvedScope
            organizationId = if (resolvedScope == CommunicationScope.ORG) activeOrgId else null
            isTemplate = if (isAppAdmin && resolvedScope == CommunicationScope.PLATFORM) request.isTemplate else false
            createdByAppUserId = principal.id
        }
        return repository.save(communication).toDto()
    }

    @Transactional
    fun updateTemplate(id: UUID, request: UpdateCommunicationRequest): CommunicationDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkWriteAccess(communication, principal, context)

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
    fun patchPublished(id: UUID, request: PatchCommunicationPublishedRequest): CommunicationDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkWriteAccess(communication, principal, context)
        if (communication.scope == CommunicationScope.PERSONAL)
        {
            throw ForbiddenException("Personal communications cannot be published")
        }
        communication.isPublished = request.isPublished
        communication.updatedAt = Timestamp.from(Instant.now())
        return repository.update(communication).toDto()
    }

    @Transactional
    fun patchStatus(id: UUID, request: PatchCommunicationStatusRequest): CommunicationDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkWriteAccess(communication, principal, context)
        communication.isActive = request.isActive
        communication.updatedAt = Timestamp.from(Instant.now())
        return repository.update(communication).toDto()
    }

    @Transactional
    fun deleteTemplate(id: UUID)
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkWriteAccess(communication, principal, context)
        communication.isDeleted = true
        communication.isActive = false
        communication.updatedAt = Timestamp.from(Instant.now())
        repository.update(communication)
        logger.info("Communication {} soft-deleted by user {}", id, principal.id)
    }

    @Transactional
    fun cloneTemplate(id: UUID, request: CloneCommunicationRequest): CommunicationDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val source = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkReadAccess(source, principal, context)

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
            createdByAppUserId = principal.id
        }
        return repository.save(clone).toDto()
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    fun preview(id: UUID, sampleVariables: Map<String, String>): RenderedCommunication
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val communication = repository.findById(id)
            ?: throw IllegalArgumentException("Communication not found: $id")
        checkReadAccess(communication, principal, context)

        val user = appUserRepository.findById(principal.id)
            ?: throw IllegalArgumentException("User not found: ${principal.id}")
        val org = context.activeOrgId?.let { organizationRepository.findById(it) }

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

    private fun checkReadAccess(communication: Communication, principal: PrincipalRef, context: AuthorizationContext)
    {
        when (communication.scope)
        {
            CommunicationScope.PERSONAL ->
                if (communication.createdByAppUserId != principal.id)
                    throw ForbiddenException("Access denied to communication ${communication.id}")
            CommunicationScope.ORG ->
            {
                val decision = authorizationService.authorize(
                    principal, Action.COMMUNICATION_VIEW, ResourceRef.communication(communication.id), context,
                )
                if (decision is Decision.Deny)
                    throw ForbiddenException("Access denied to communication ${communication.id}")
            }
            CommunicationScope.PLATFORM -> { }
        }
    }

    private fun checkWriteAccess(communication: Communication, principal: PrincipalRef, context: AuthorizationContext)
    {
        when (communication.scope)
        {
            CommunicationScope.PERSONAL ->
                if (communication.createdByAppUserId != principal.id)
                    throw ForbiddenException("Access denied to communication ${communication.id}")
            CommunicationScope.ORG ->
            {
                val decision = authorizationService.authorize(
                    principal, Action.COMMUNICATION_EDIT, ResourceRef.communication(communication.id), context,
                )
                if (decision is Decision.Deny)
                    throw ForbiddenException("Access denied to communication ${communication.id}")
            }
            CommunicationScope.PLATFORM ->
                if (!userRoleService.isAppAdmin(principal.id))
                    throw ForbiddenException("App admin role required to modify PLATFORM-scoped communications")
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
    ): CommunicationScope
    {
        if (requestedScope != null)
        {
            val parsed = runCatching { CommunicationScope.valueOf(requestedScope.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid scope: $requestedScope") }
            if (parsed == CommunicationScope.PLATFORM && !isAppAdmin)
                throw ForbiddenException("App admin role required to create PLATFORM-scoped communications")
            if (parsed == CommunicationScope.ORG && !isOrgAdmin)
                throw ForbiddenException("Org admin role required to create ORG-scoped communications")
            return parsed
        }
        return when
        {
            isOrgAdmin && activeOrgId != null -> CommunicationScope.ORG
            isAppAdmin -> CommunicationScope.PLATFORM
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
