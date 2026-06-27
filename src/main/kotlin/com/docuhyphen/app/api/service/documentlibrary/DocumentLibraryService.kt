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
import com.docuhyphen.app.api.repository.DocumentLibraryRepository
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
)
{
    private val logger = LoggerFactory.getLogger(DocumentLibraryService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    // ── Read ──────────────────────────────────────────────────────────────────

    fun listEntries(
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
        scope: String?,
        tag: String?,
    ): List<DocumentLibraryEntrySummaryDto>
    {
        return repository.findAllAccessibleForCaller(callerUserId, callerOrgId, isOrgAdmin, isAppAdmin)
            .asSequence()
            .filter { scope == null || it.scope.name == scope.uppercase() }
            .filter { tag == null || decodeTags(it.generalTags).contains(tag) }
            .map { it.toSummaryDto() }
            .toList()
    }

    fun getEntry(id: UUID, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean): DocumentLibraryEntryDto
    {
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkReadAccess(entry, callerUserId, callerOrgId, isAppAdmin)
        return entry.toDto()
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    fun createEntry(
        request: CreateDocumentLibraryEntryRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): DocumentLibraryEntryDto
    {
        val resolvedScope = resolveScope(request.scope, callerOrgId, isOrgAdmin, isAppAdmin)
        val entry = DocumentLibraryEntry().apply {
            title = request.title.trim()
            description = request.description?.trim()
            generalTags = encodeTags(request.generalTags)
            scope = resolvedScope
            organizationId = if (resolvedScope == BlueprintScope.PERSONAL) null else callerOrgId
            createdByAppUserId = callerUserId
            restrictType = request.restrictType
            restrictedType = request.restrictedType
            required = request.required
        }
        return repository.save(entry).toDto()
    }

    @Transactional
    fun updateEntry(
        id: UUID,
        request: UpdateDocumentLibraryEntryRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): DocumentLibraryEntryDto
    {
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, callerUserId, callerOrgId, isAppAdmin)

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
    fun uploadFile(
        id: UUID,
        file: File,
        extension: String,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): DocumentLibraryEntryDto
    {
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, callerUserId, callerOrgId, isAppAdmin)

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

    fun downloadFile(
        id: UUID,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): File
    {
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkReadAccess(entry, callerUserId, callerOrgId, isAppAdmin)

        val path = entry.storagePath
            ?: throw IllegalArgumentException("No file uploaded for document library entry $id")

        return fileStorageService.downloadDocument(path)
    }

    @Transactional
    fun patchStatus(
        id: UUID,
        request: PatchDocumentLibraryStatusRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): DocumentLibraryEntryDto
    {
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, callerUserId, callerOrgId, isAppAdmin)
        entry.isActive = request.isActive
        entry.updatedAt = Timestamp.from(Instant.now())
        return repository.update(entry).toDto()
    }

    @Transactional
    fun patchPublished(
        id: UUID,
        request: PatchDocumentLibraryPublishedRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isAppAdmin: Boolean,
    ): DocumentLibraryEntryDto
    {
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, callerUserId, callerOrgId, isAppAdmin)
        if (entry.scope == BlueprintScope.PERSONAL)
        {
            throw ForbiddenException("Personal library entries cannot be published")
        }
        entry.isPublished = request.isPublished
        entry.updatedAt = Timestamp.from(Instant.now())
        return repository.update(entry).toDto()
    }

    @Transactional
    fun deleteEntry(id: UUID, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        val entry = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkWriteAccess(entry, callerUserId, callerOrgId, isAppAdmin)
        entry.isDeleted = true
        entry.isActive = false
        entry.updatedAt = Timestamp.from(Instant.now())
        repository.update(entry)
        logger.info("Document library entry {} soft-deleted by user {}", id, callerUserId)
    }

    @Transactional
    fun cloneEntry(
        id: UUID,
        request: CloneDocumentLibraryEntryRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): DocumentLibraryEntryDto
    {
        val source = repository.findById(id)
            ?: throw IllegalArgumentException("Document library entry not found: $id")
        checkReadAccess(source, callerUserId, callerOrgId, isAppAdmin)

        val targetScope = resolveCloneTargetScope(request.targetScope, callerOrgId, isOrgAdmin, isAppAdmin)
        val clone = DocumentLibraryEntry().apply {
            title = request.newName?.trim()?.ifBlank { null } ?: "${source.title} (copy)"
            description = source.description
            generalTags = source.generalTags
            isActive = false
            scope = targetScope
            organizationId = if (targetScope == BlueprintScope.ORG) callerOrgId else null
            sourceDocumentId = source.id
            createdByAppUserId = callerUserId
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

    private fun checkReadAccess(entry: DocumentLibraryEntry, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        if (isAppAdmin) return
        when (entry.scope)
        {
            BlueprintScope.PERSONAL ->
            {
                if (entry.createdByAppUserId != callerUserId)
                    throw ForbiddenException("Access denied to document library entry ${entry.id}")
            }
            BlueprintScope.ORG ->
            {
                if (callerOrgId == null || entry.organizationId != callerOrgId)
                    throw ForbiddenException("Access denied to document library entry ${entry.id}")
            }
            BlueprintScope.APP -> { }
        }
    }

    private fun checkWriteAccess(entry: DocumentLibraryEntry, callerUserId: UUID, callerOrgId: UUID?, isAppAdmin: Boolean)
    {
        if (isAppAdmin) return
        when (entry.scope)
        {
            BlueprintScope.PERSONAL ->
            {
                if (entry.createdByAppUserId != callerUserId)
                    throw ForbiddenException("Access denied to document library entry ${entry.id}")
            }
            BlueprintScope.ORG ->
            {
                if (callerOrgId == null || entry.organizationId != callerOrgId)
                    throw ForbiddenException("Access denied to document library entry ${entry.id}")
            }
            BlueprintScope.APP ->
                throw ForbiddenException("Platform library entries cannot be modified directly; clone them instead")
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
                throw ForbiddenException("App admin role required to create APP-scoped library entries")
            if (parsed == BlueprintScope.ORG && !isOrgAdmin && !isAppAdmin)
                throw ForbiddenException("Org admin role required to create ORG-scoped library entries")
            return parsed
        }
        return when
        {
            isAppAdmin -> BlueprintScope.APP
            isOrgAdmin && callerOrgId != null -> BlueprintScope.ORG
            else -> BlueprintScope.PERSONAL
        }
    }

    private fun resolveCloneTargetScope(
        requested: String?,
        callerOrgId: UUID?,
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
                if (callerOrgId == null)
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
}
