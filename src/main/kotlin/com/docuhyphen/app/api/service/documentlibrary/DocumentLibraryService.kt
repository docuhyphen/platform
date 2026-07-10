package com.docuhyphen.app.api.service.documentlibrary

import com.docuhyphen.app.api.model.dto.CloneDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.CreateDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.dto.DocumentLibraryEntrySummaryDto
import com.docuhyphen.app.api.model.dto.DocumentLibraryEntryDto
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchDocumentLibraryStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateDocumentLibraryEntryRequest
import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.model.entity.DocumentLibraryEntry
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.DocumentLibraryRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.storage.FileStorageService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class DocumentLibraryService @Inject constructor(
    private val repository: DocumentLibraryRepository,
    private val fileStorageService: FileStorageService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val userRoleService: UserRoleService,
    private val auditRecorder: AuditRecorder,
)
{
    private val logger = LoggerFactory.getLogger(DocumentLibraryService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun listEntries(scope: String?, tag: String?): List<DocumentLibraryEntrySummaryDto>
    {
        val principal = currentPrincipal()
        val activeOrgId = currentContext().activeOrgId
        val isOrgAdmin = activeOrgId != null && userRoleService.isOrgAdminIn(principal.id, activeOrgId)
        val isAppAdmin = userRoleService.isAppAdmin(principal.id)

        return repository.findAllAccessibleForCaller(principal.id, activeOrgId, isOrgAdmin, isAppAdmin)
            .asSequence()
            .filter { scope == null || it.scope.name == scope.uppercase() }
            .filter { tag == null || decodeTags(it.generalTags).contains(tag) }
            .map { it.toSummaryDto() }
            .toList()
    }

    fun getEntry(id: UUID): DocumentLibraryEntryDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkReadAccess(entry, principal, context)
        return entry.toDto()
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    fun createEntry(request: CreateDocumentLibraryEntryRequest): DocumentLibraryEntryDto
    {
        val principal = currentPrincipal()
        val activeOrgId = currentContext().activeOrgId
        val isOrgAdmin = activeOrgId != null && userRoleService.isOrgAdminIn(principal.id, activeOrgId)
        val isAppAdmin = userRoleService.isAppAdmin(principal.id)

        val resolvedScope = resolveScope(request.scope, activeOrgId, isOrgAdmin, isAppAdmin)
        val entry = DocumentLibraryEntry().apply {
            title = request.title.trim()
            description = request.description?.trim()
            generalTags = encodeTags(request.generalTags)
            scope = resolvedScope
            organizationId = if (resolvedScope == BlueprintScope.PERSONAL) null else activeOrgId
            createdByAppUserId = principal.id
            restrictType = request.restrictType
            restrictedType = request.restrictedType
            required = request.required
        }
        return repository.save(entry).toDto()
    }

    @Transactional
    fun updateEntry(id: UUID, request: UpdateDocumentLibraryEntryRequest): DocumentLibraryEntryDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, principal, context)

        request.title?.trim()?.let { if (it.isNotBlank()) entry.title = it }
        request.description?.let { entry.description = it.trim().ifBlank { null } }
        request.generalTags?.let { entry.generalTags = encodeTags(it) }
        request.restrictType?.let { entry.restrictType = it }
        request.restrictedType?.let { entry.restrictedType = it }
        request.required?.let { entry.required = it }
        entry.updatedAt = Timestamp.from(Instant.now())

        return repository.update(entry).toDto()
    }

    @Transactional
    fun uploadFile(id: UUID, file: File, extension: String): DocumentLibraryEntryDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, principal, context)

        val ext = extension.lowercase().trimStart('.')
        val storageKey = "lib/${entry.id}.$ext"
        fileStorageService.uploadDocument(file, storageKey)

        entry.storagePath = storageKey
        entry.documentType = ext.uppercase()
        entry.fileName = "${entry.title}.$ext"
        entry.fileSizeBytes = file.length()
        entry.contentHash = "hash"
        entry.updatedAt = Timestamp.from(Instant.now())

        return repository.update(entry).toDto()
    }

    fun downloadFile(id: UUID): File
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkReadAccess(entry, principal, context)

        val path = entry.storagePath
            ?: throw IllegalArgumentException("No file uploaded for document library entry $id")

        recordLibraryDownloadEvent(entry, principal.id, context.activeOrgId)

        return fileStorageService.downloadDocument(path)
    }

    @Transactional
    fun patchStatus(id: UUID, request: PatchDocumentLibraryStatusRequest): DocumentLibraryEntryDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, principal, context)
        entry.isActive = request.isActive
        entry.updatedAt = Timestamp.from(Instant.now())
        return repository.update(entry).toDto()
    }

    @Transactional
    fun patchPublished(id: UUID, request: PatchDocumentLibraryPublishedRequest): DocumentLibraryEntryDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, principal, context)
        if (entry.scope == BlueprintScope.PERSONAL)
        {
            throw ForbiddenException("Personal library entries cannot be published")
        }
        entry.isPublished = request.isPublished
        entry.updatedAt = Timestamp.from(Instant.now())
        return repository.update(entry).toDto()
    }

    @Transactional
    fun deleteEntry(id: UUID)
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, principal, context)
        entry.isDeleted = true
        entry.isActive = false
        entry.updatedAt = Timestamp.from(Instant.now())
        repository.update(entry)
        logger.info("Document library entry {} soft-deleted by user {}", id, principal.id)
    }

    @Transactional
    fun cloneEntry(id: UUID, request: CloneDocumentLibraryEntryRequest): DocumentLibraryEntryDto
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val activeOrgId = context.activeOrgId
        val isOrgAdmin = activeOrgId != null && userRoleService.isOrgAdminIn(principal.id, activeOrgId)
        val isAppAdmin = userRoleService.isAppAdmin(principal.id)

        val source = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkReadAccess(source, principal, context)

        val targetScope = resolveCloneTargetScope(request.targetScope, activeOrgId, isOrgAdmin, isAppAdmin)
        val clone = DocumentLibraryEntry().apply {
            title = request.newName?.trim()?.ifBlank { null } ?: "${source.title} (copy)"
            description = source.description
            generalTags = source.generalTags
            isActive = false
            scope = targetScope
            organizationId = if (targetScope == BlueprintScope.ORG) activeOrgId else null
            sourceDocumentId = source.id
            createdByAppUserId = principal.id
        }
        return repository.save(clone).toDto()
    }

    fun resolveLibraryFileForBlueprintDocument(libraryDocumentId: UUID): File?
    {
        val entry = repository.findById(libraryDocumentId) ?: return null
        val path = entry.storagePath ?: return null
        return runCatching { fileStorageService.downloadDocument(path) }.getOrNull()
    }

    // ── Access control ────────────────────────────────────────────────────────

    private fun checkReadAccess(entry: DocumentLibraryEntry, principal: PrincipalRef, context: AuthorizationContext)
    {
        if (userRoleService.isAppAdmin(principal.id)) return
        when (entry.scope)
        {
            BlueprintScope.PERSONAL ->
                if (entry.createdByAppUserId != principal.id)
                    throw ForbiddenException("Access denied to document library entry ${entry.id}")
            BlueprintScope.ORG ->
            {
                val decision = authorizationService.authorize(
                    principal, Action.DOC_LIBRARY_VIEW, ResourceRef.docLibrary(entry.id), context,
                )
                if (decision is Decision.Deny)
                    throw ForbiddenException("Access denied to document library entry ${entry.id}")
            }
            BlueprintScope.APP -> { }
        }
    }

    private fun checkWriteAccess(entry: DocumentLibraryEntry, principal: PrincipalRef, context: AuthorizationContext)
    {
        if (userRoleService.isAppAdmin(principal.id)) return
        when (entry.scope)
        {
            BlueprintScope.PERSONAL ->
                if (entry.createdByAppUserId != principal.id)
                    throw ForbiddenException("Access denied to document library entry ${entry.id}")
            BlueprintScope.ORG ->
            {
                val decision = authorizationService.authorize(
                    principal, Action.DOC_LIBRARY_EDIT, ResourceRef.docLibrary(entry.id), context,
                )
                if (decision is Decision.Deny)
                    throw ForbiddenException("Access denied to document library entry ${entry.id}")
            }
            BlueprintScope.APP ->
                throw ForbiddenException("Platform library entries cannot be modified directly; clone them instead")
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
                throw ForbiddenException("App admin role required to create APP-scoped library entries")
            if (parsed == BlueprintScope.ORG && !isOrgAdmin && !isAppAdmin)
                throw ForbiddenException("Org admin role required to create ORG-scoped library entries")
            return parsed
        }
        return when
        {
            isAppAdmin -> BlueprintScope.APP
            isOrgAdmin && activeOrgId != null -> BlueprintScope.ORG
            else -> BlueprintScope.PERSONAL
        }
    }

    private fun resolveCloneTargetScope(
        requested: String?,
        activeOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): BlueprintScope
    {
        if (requested == null) return BlueprintScope.PERSONAL
        return when (requested.uppercase())
        {
            "PERSONAL" -> BlueprintScope.PERSONAL
            "ORG" ->
            {
                if (!isOrgAdmin && !isAppAdmin)
                    throw ForbiddenException("Org admin role required to clone into the organization collection")
                if (activeOrgId == null)
                    throw ForbiddenException("No organization membership found")
                BlueprintScope.ORG
            }
            else -> throw ForbiddenException("Cannot clone directly into scope: $requested")
        }
    }

    private fun decodeTags(tagsJson: String): List<String> =
        runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), tagsJson)
        }.getOrDefault(emptyList())

    private fun encodeTags(tags: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), tags)

    private fun DocumentLibraryEntry.toDto() = DocumentLibraryEntryDto(
        id = id,
        title = title,
        description = description,
        scope = scope.name,
        organizationId = organizationId,
        createdByAppUserId = createdByAppUserId,
        documentType = documentType,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        contentHash = contentHash,
        isPublished = isPublished,
        isActive = isActive,
        hasFile = storagePath != null,
        restrictType = restrictType,
        restrictedType = restrictedType,
        required = required,
        generalTags = decodeTags(generalTags),
        sourceDocumentId = sourceDocumentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun DocumentLibraryEntry.toSummaryDto() = DocumentLibraryEntrySummaryDto(
        id = id,
        title = title,
        description = description,
        scope = scope.name,
        organizationId = organizationId,
        createdByAppUserId = createdByAppUserId,
        documentType = documentType,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        isPublished = isPublished,
        isActive = isActive,
        hasFile = storagePath != null,
        restrictType = restrictType,
        restrictedType = restrictedType,
        required = required,
        generalTags = decodeTags(generalTags),
        sourceDocumentId = sourceDocumentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    /**
     * Phase 3 task 2: document library download had zero capture before this phase. Failures are
     * caught and logged, never propagated, so audit plumbing can never break an actual file
     * download response.
     */
    private fun recordLibraryDownloadEvent(entry: DocumentLibraryEntry, actorId: UUID, organizationId: UUID?)
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = AuditEventType.DOCUMENT_LIBRARY_DOWNLOAD.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = ResourceType.DOC_LIBRARY.name,
                    targetId = entry.id.toString(),
                    targetLabel = entry.title,
                    organizationId = organizationId ?: entry.organizationId,
                    payload = mapOf("title" to entry.title, "scope" to entry.scope.name),
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("DocumentLibraryService: AuditRecorder rejected draft for library download: {}", e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("DocumentLibraryService: AuditRecorder capture failed (fail-closed) for library download: {}", e.message, e)
        }
    }
}
