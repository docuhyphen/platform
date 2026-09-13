package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.blueprint.BlueprintDefinitionRepository
import com.docuhyphen.app.api.repository.blueprint.BlueprintDocumentDefaultRepository
import com.docuhyphen.app.api.repository.blueprint.BlueprintFieldDefaultRepository
import com.docuhyphen.app.api.repository.blueprint.BlueprintParticipantDefaultRepository
import com.docuhyphen.app.api.repository.documentlibrary.DocumentLibraryRepository
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTemplateReferenceService
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
import java.util.*

data class BlueprintDocumentInstantiationDefault(
    val default: BlueprintDocumentDefault,
    val libraryEntry: DocumentLibraryEntry?,
)

data class BlueprintInformationRequestInstantiationSnapshot(
    val blueprintDefinitionId: UUID,
    val templateVersionId: UUID,
    val participantDefaults: List<BlueprintParticipantDefault>,
    val documentDefaults: List<BlueprintDocumentInstantiationDefault>,
    val fieldDefaults: List<BlueprintFieldDefault>,
)

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
    private val templateReferenceService: InformationRequestTemplateReferenceService,
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
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkReadAccess(bp, principal, context)
        blueprintSubscriptionGuard.requireExistingBlueprintUse(
            appUserId = principal.id,
            scope = bp.scope,
            createdByAppUserId = bp.createdByAppUserId,
            organizationId = bp.organizationId,
            activeOrganizationId = context.activeOrgId,
        )
        return bp.toDto()
    }

    /**
     * The exact Information Request Template Version a request created from this blueprint now
     * would be pinned to, or null when the blueprint requests no information.
     *
     * The question is asked again on every instantiation rather than trusted from the moment the
     * Version was named, because the named Version may have been retired since. Requests already
     * created keep resolving the Version they pinned; only new ones are refused.
     */
    fun resolveInformationRequestTemplateVersionForInstantiation(id: UUID): UUID?
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkReadAccess(bp, principal, context)

        val namedVersionId = bp.informationRequestTemplateVersionId ?: return null
        return templateReferenceService.requireInstantiableVersion(namedVersionId).templateVersionId
    }

    fun loadInformationRequestInstantiationSnapshot(
        id: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    ): BlueprintInformationRequestInstantiationSnapshot
    {
        val bp = repository.findById(id)
            ?: throw IllegalArgumentException("Blueprint not found: $id")
        checkReadAccess(bp, principal, context)
        val namedVersionId = bp.informationRequestTemplateVersionId
            ?: throw IllegalStateException("Blueprint does not name an Information Request Template Version")
        val reference = templateReferenceService.requireInstantiableVersion(namedVersionId)
        return BlueprintInformationRequestInstantiationSnapshot(
            blueprintDefinitionId = bp.id,
            templateVersionId = reference.templateVersionId,
            participantDefaults = participantDefaultRepository.findAllByBlueprintDefinitionId(bp.id),
            documentDefaults = documentDefaultRepository.findAllByBlueprintDefinitionId(bp.id).map { default ->
                BlueprintDocumentInstantiationDefault(
                    default,
                    default.libraryDocumentId?.let(documentLibraryRepository::findById)
                )
            },
            fieldDefaults = fieldDefaultRepository.findAllByBlueprintDefinitionId(bp.id),
        )
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
            informationRequestTemplateVersionId = request.informationRequestTemplateVersionId?.let {
                selectTemplateVersion(it, resolvedScope, activeOrgId, principal.id)
            }
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
        applyTemplateVersionChange(bp, request, principal.id)
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
            informationRequestTemplateVersionId = carriedTemplateVersion(
                source, targetScope, activeOrgId, principal.id,
            )
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

    // ── Information Request Template Version reference ────────────────────────

    /**
     * Resolves the exact Version this blueprint is about to name and refuses one its own owner does
     * not hold. Reusable request configuration belongs to an owner, and a blueprint that named
     * another owner's Version would be issuing configuration it has no claim to.
     */
    private fun selectTemplateVersion(
        templateVersionId: UUID,
        scope: BlueprintScope,
        activeOrgId: UUID?,
        callerUserId: UUID,
    ): UUID
    {
        val reference = templateReferenceService.requireSelectableVersion(templateVersionId)
        val owned = when (scope)
        {
            BlueprintScope.ORG ->
                reference.ownerScopeKind == InformationRequestTemplateScopeKind.ORGANIZATION &&
                        reference.ownerOrganizationId != null &&
                        reference.ownerOrganizationId == activeOrgId

            BlueprintScope.PERSONAL ->
                reference.ownerScopeKind == InformationRequestTemplateScopeKind.PERSONAL &&
                        reference.ownerUserId == callerUserId
            // A platform blueprint answers to no organization and no person, so there is no owner
            // whose entitlement and release decisions the request it would create could be made
            // against.
            BlueprintScope.APP -> false
        }
        if (!owned)
        {
            throw ForbiddenException(
                "This blueprint cannot request information against a template version its owner " +
                        "does not hold",
            )
        }
        return reference.templateVersionId
    }

    /**
     * Applies a stated change to the named Version and leaves an unstated one alone. Editing the
     * rest of a blueprint must not depend on the named Version still being publishable, otherwise
     * retiring a Version would freeze every blueprint that named it.
     */
    private fun applyTemplateVersionChange(
        bp: BlueprintDefinition,
        request: UpdateBlueprintRequest,
        callerUserId: UUID,
    )
    {
        if (request.clearInformationRequestTemplateVersion)
        {
            if (request.informationRequestTemplateVersionId != null)
            {
                throw IllegalArgumentException(
                    "An information request template version cannot be selected and cleared in the " +
                            "same update",
                )
            }
            bp.informationRequestTemplateVersionId = null
            return
        }
        request.informationRequestTemplateVersionId?.let {
            bp.informationRequestTemplateVersionId =
                selectTemplateVersion(it, bp.scope, bp.organizationId, callerUserId)
        }
    }

    /**
     * The Version a copy keeps. A copy made for another owner starts without one, because one
     * owner's reusable configuration is not another's to issue and carrying the name across would
     * produce a blueprint whose every instantiation was refused.
     */
    private fun carriedTemplateVersion(
        source: BlueprintDefinition,
        targetScope: BlueprintScope,
        activeOrgId: UUID?,
        callerUserId: UUID,
    ): UUID?
    {
        val named = source.informationRequestTemplateVersionId ?: return null
        val sameOwner = when (targetScope)
        {
            BlueprintScope.ORG ->
                source.scope == BlueprintScope.ORG &&
                        source.organizationId != null &&
                        source.organizationId == activeOrgId

            BlueprintScope.PERSONAL ->
                source.scope == BlueprintScope.PERSONAL &&
                        source.createdByAppUserId == callerUserId

            BlueprintScope.APP -> false
        }
        return if (sameOwner) named else null
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

        // A blueprint's defaults are the values the Exchange it creates starts with, so the schema
        // holding them has to be a schema an Exchange can be given.
        val validated = schemaDefinitionService.validateDefaultsForSchema(
            schemaDefinitionId,
            nonEmpty.map { it.fieldDefinitionId to it.value!! },
            ResourceType.EXCHANGE.name,
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
        informationRequestTemplateVersionId = informationRequestTemplateVersionId,
        exchangeDocuments = loadDocuments(id),
        participants = loadParticipants(id),
        fieldDefaults = loadFieldDefaults(id),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
