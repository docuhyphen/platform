package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestTemplateDtoMapper
import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSummaryDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
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
import com.docuhyphen.app.api.service.auth.authz.RoleCapabilities
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Authoring of reusable Information Request Templates and of the one editable Version each may hold.
 *
 * Three separate questions are answered before anything is authored, and none of them stands in for
 * another: whether the caller may act for the owner the configuration names, whether that owner is
 * entitled to the capability, and whether this deployment has released it to them. The first is a
 * role and Share decision, the second and third belong to
 * [InformationRequestTemplateEntitlementGuard], and both of the latter are asked about the stored
 * owner rather than about whichever organization the caller currently has selected.
 *
 * A draft is edited as a whole document rather than in parts, so this service resolves the owner and
 * the editable Version and hands the document to
 * [InformationRequestTemplateConfigurationWriter]. Freezing a Version, retiring one, and starting a
 * new one after a Version has frozen are separate lifecycle operations and are not this class.
 */
@ApplicationScoped
class InformationRequestTemplateAuthoringService @Inject constructor(
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val configurationWriter: InformationRequestTemplateConfigurationWriter,
    private val projectionLoader: InformationRequestTemplateProjectionLoader,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val userRoleService: UserRoleService,
    private val auditRecorder: AuditRecorder,
    private val entitlementGuard: InformationRequestTemplateEntitlementGuard,
    private val schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
)
{
    internal fun requireMutationContext(id: UUID): InformationRequestTemplateMutationContext
    {
        val definition = requireDefinition(id)
        val principal = requireOwnerAccess(definition, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)
        entitlementGuard.requireTemplateMutation(
            definition.scopeKind, definition.scopeOrgId, definition.scopeUserId,
            entitlementOrganizationId(definition.scopeKind),
        )
        return InformationRequestTemplateMutationContext(definition, principal)
    }

    internal fun projectTemplate(definition: InformationRequestTemplateDefinition): InformationRequestTemplateDto =
        toDto(definition)

    // ── Reads ─────────────────────────────────────────────────────────────────

    /**
     * The Templates of one owner: the caller's selected organization when it names one, otherwise
     * the caller's own. A caller never sees both at once, because the two are separate owners with
     * separate entitlements rather than two views of one library.
     */
    fun listTemplates(
        scopeKind: InformationRequestTemplateScopeKind? = null,
    ): List<InformationRequestTemplateSummaryDto>
    {
        val principal = requireUserPrincipal()
        val resolved = scopeKind ?: defaultScopeKind()
        val definitions = when (resolved)
        {
            InformationRequestTemplateScopeKind.ORGANIZATION ->
            {
                val organizationId = requireSelectedOrganization(
                    principal, Action.INFORMATION_REQUEST_TEMPLATE_VIEW,
                )
                entitlementGuard.requireTemplateAccess(resolved, organizationId, null, null)
                definitionRepository.findAllForOrganization(organizationId)
            }
            InformationRequestTemplateScopeKind.PERSONAL ->
            {
                entitlementGuard.requireTemplateAccess(
                    resolved, null, principal.id, entitlementOrganizationId(resolved),
                )
                definitionRepository.findAllForUser(principal.id)
            }
            InformationRequestTemplateScopeKind.PLATFORM -> definitionRepository.findAllPlatform()
        }

        // One read of every listed Template's versions. Asking each Template separately, and then
        // projecting the configuration behind each answer, would cost a multiple of the list length.
        val versionsByDefinition = versionRepository
            .findForDefinitions(definitions.map { it.id })
            .groupBy { it.templateDefinitionId }

        return definitions.map { definition ->
            InformationRequestTemplateDtoMapper.toSummaryDto(
                definition = definition,
                versions = versionsByDefinition[definition.id].orEmpty(),
            )
        }
    }

    fun getTemplate(id: UUID): InformationRequestTemplateDto
    {
        val definition = requireDefinition(id)
        if (definition.scopeKind == InformationRequestTemplateScopeKind.PLATFORM)
        {
            requireUserPrincipal()
            return toPublishedDto(definition)
        }
        requireOwnerAccess(definition, Action.INFORMATION_REQUEST_TEMPLATE_VIEW)
        entitlementGuard.requireTemplateAccess(
            definition.scopeKind, definition.scopeOrgId, definition.scopeUserId,
            entitlementOrganizationId(definition.scopeKind),
        )
        return toDto(definition)
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Creates a Template identity together with the first Version an author can edit. The two are
     * created at once because a Template with no editable Version is a name nothing can be said
     * about, and an author would have to ask for one before doing anything at all.
     */
    @Transactional
    fun createTemplate(request: CreateInformationRequestTemplateRequest): InformationRequestTemplateDto
    {
        val principal = requireUserPrincipal()
        val scopeKind = request.scopeKind ?: defaultScopeKind()
        val organizationId = if (scopeKind == InformationRequestTemplateScopeKind.ORGANIZATION)
            requireSelectedOrganization(principal, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)
        else
            null
        val userId = if (scopeKind == InformationRequestTemplateScopeKind.PERSONAL) principal.id else null

        entitlementGuard.requireTemplateMutation(
            scopeKind, organizationId, userId, entitlementOrganizationId(scopeKind),
        )

        val namespace = machineKey(request.namespace, "namespace")
        val templateKey = machineKey(request.templateKey, "templateKey")
        val displayName = request.displayName.trim().ifBlank {
            throw InformationRequestTemplateValidationException("displayName is required")
        }

        if (definitionRepository.findByKey(scopeKind, organizationId, userId, namespace, templateKey) != null)
        {
            throw InformationRequestTemplateValidationException(
                "An information request template with key $namespace:$templateKey already exists " +
                    "for this owner",
            )
        }

        val definition = definitionRepository.save(
            InformationRequestTemplateDefinition().apply {
                this.scopeKind = scopeKind
                this.scopeOrgId = organizationId
                this.scopeUserId = userId
                this.namespace = namespace
                this.templateKey = templateKey
                this.displayName = displayName
                this.description = request.description?.trim()?.ifBlank { null }
                this.createdByAppUserId = principal.id
            },
        )
        versionRepository.save(
            InformationRequestTemplateVersion().apply {
                templateDefinitionId = definition.id
                versionNumber = FIRST_VERSION_NUMBER
                createdByAppUserId = principal.id
            },
        )

        recordTemplateEvent(AuditEventType.INFORMATION_REQUEST_TEMPLATE_CREATE, definition, principal.id)
        return toDto(definition)
    }

    /**
     * Replaces everything the editable Version configures. There is nothing to merge into, because a
     * configuration is one authored statement rather than a set of independently editable settings.
     *
     * @throws IllegalStateException when every Version of the Template has frozen. Continuing to
     * author needs a new Version, which is a decision the author makes rather than a side effect of
     * saving.
     */
    @Transactional
    fun replaceDraftConfiguration(
        id: UUID,
        request: InformationRequestTemplateConfigurationRequest,
    ): InformationRequestTemplateDto
    {
        val mutation = requireMutationContext(id)
        val definition = mutation.definition

        // Asked before the draft is located, so a document naming a contract this owner cannot
        // resolve against is refused without any part of the previous one being cleared.
        schemaCompatibility.requireUsable(definition, request.schemaVersionId)

        val draft = versionRepository.findDraftForUpdate(definition.id)
            ?: throw IllegalStateException(
                "Information request template $id has no editable version",
            )
        configurationWriter.replaceConfiguration(draft, request)

        definition.updatedAt = Timestamp.from(Instant.now())
        definitionRepository.update(definition)

        recordTemplateEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_CONFIGURE,
            definition,
            mutation.principal.id,
        )
        return toDto(definition)
    }

    // ── Owner resolution and access ───────────────────────────────────────────

    /**
     * Which owner a caller who named none is authoring for. A selected organization means the caller
     * is acting for it; no selection means the caller is acting personally. There is no fallback to
     * a primary organization, because that would bill and audit an organization the caller did not
     * choose to act for.
     */
    private fun defaultScopeKind(): InformationRequestTemplateScopeKind =
        if (currentContext().activeOrgId != null)
            InformationRequestTemplateScopeKind.ORGANIZATION
        else
            InformationRequestTemplateScopeKind.PERSONAL

    /** Returns the acting principal so callers that need it do not resolve it a second time. */
    private fun requireOwnerAccess(
        definition: InformationRequestTemplateDefinition,
        action: Action,
    ): PrincipalRef
    {
        val principal = requireUserPrincipal()
        when (definition.scopeKind)
        {
            InformationRequestTemplateScopeKind.ORGANIZATION ->
            {
                val organizationId = definition.scopeOrgId
                    ?: throw ForbiddenException("Organization-owned information request template has no owner")
                if (currentContext().activeOrgId == organizationId &&
                    hasOrganizationAccess(principal, organizationId, action)
                )
                {
                    return principal
                }
            }
            // A person authoring for themselves is the owner or nobody. No role or Share widens it,
            // because there is no organization for one to be granted in.
            InformationRequestTemplateScopeKind.PERSONAL ->
            {
                if (definition.scopeUserId == principal.id)
                {
                    return principal
                }
            }
            InformationRequestTemplateScopeKind.PLATFORM -> Unit
        }
        throw ForbiddenException("Access denied to information request template configuration")
    }

    private fun requireSelectedOrganization(principal: PrincipalRef, action: Action): UUID
    {
        val organizationId = currentContext().activeOrgId
            ?: throw ForbiddenException("An active organization is required")
        if (!hasOrganizationAccess(principal, organizationId, action))
        {
            throw ForbiddenException("Access denied to information request template configuration")
        }
        return organizationId
    }

    private fun hasOrganizationAccess(principal: PrincipalRef, organizationId: UUID, action: Action): Boolean
    {
        val holdsCapability = userRoleService.orgRolesIn(principal.id, organizationId)
            .any { action.required in RoleCapabilities.forOrganizationRole(it) }
        if (!holdsCapability)
        {
            return false
        }
        return authorizationService.authorize(
            principal,
            action,
            ResourceRef.organization(organizationId),
            currentContext(),
        ) is Decision.Allow
    }

    private fun requireDefinition(id: UUID): InformationRequestTemplateDefinition =
        definitionRepository.findById(id)
            ?: throw IllegalArgumentException("Information request template not found: $id")

    /**
     * Reusable configuration is authored by a person. An application credential has no personal
     * owner to bill or audit, so it is turned away here rather than resolving to one by accident.
     */
    private fun requireUserPrincipal(): PrincipalRef
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Not authenticated")
        if (principal.kind != PrincipalKind.USER)
        {
            throw ForbiddenException("Information request template configuration is authored by a user")
        }
        return principal
    }

    private fun currentContext(): AuthorizationContext = authorizationContextFactory.currentContext()

    private fun entitlementOrganizationId(scopeKind: InformationRequestTemplateScopeKind): UUID? =
        currentContext().activeOrgId.takeIf { scopeKind == InformationRequestTemplateScopeKind.PERSONAL }

    private fun machineKey(candidate: String, label: String): String =
        InformationRequestTemplateKey.normalizeOrNull(candidate)
            ?: throw InformationRequestTemplateValidationException(
                InformationRequestTemplateKey.refusalFor(label, candidate),
            )

    // ── Audit ─────────────────────────────────────────────────────────────────

    /**
     * Files the change under the owner that holds the configuration. A personally owned Template
     * files under that person; nothing here falls back to platform scope, because a personal
     * Template recorded as the platform's would be readable by the wrong audience.
     */
    internal fun recordTemplateEvent(
        eventType: AuditEventType,
        definition: InformationRequestTemplateDefinition,
        actorId: UUID,
    )
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    owner = auditOwnerOf(definition),
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = TEMPLATE_TARGET_TYPE,
                    targetId = definition.id.toString(),
                    targetLabel = definition.displayName,
                    payload = mapOf(
                        "namespace" to definition.namespace,
                        "templateKey" to definition.templateKey,
                        "scopeKind" to definition.scopeKind.name,
                    ),
                ),
            )
        }
        catch (exception: AuditDraftInvalidException)
        {
            logger.warn(
                "InformationRequestTemplateAuthoringService: AuditRecorder rejected {} draft: {}",
                eventType.key,
                exception.message,
            )
        }
        catch (exception: AuditCaptureFailedException)
        {
            logger.error(
                "InformationRequestTemplateAuthoringService: AuditRecorder capture failed for {}: {}",
                eventType.key,
                exception.message,
                exception,
            )
        }
    }

    private fun auditOwnerOf(definition: InformationRequestTemplateDefinition): AuditOwnerScope =
        when (definition.scopeKind)
        {
            InformationRequestTemplateScopeKind.ORGANIZATION -> AuditOwnerScope.Organization(
                requireNotNull(definition.scopeOrgId) {
                    "Organization-owned information request template names no organization"
                },
            )
            InformationRequestTemplateScopeKind.PERSONAL -> AuditOwnerScope.Personal(
                requireNotNull(definition.scopeUserId) {
                    "Personally owned information request template names no person"
                },
            )
            InformationRequestTemplateScopeKind.PLATFORM -> AuditOwnerScope.Platform
        }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDto(definition: InformationRequestTemplateDefinition): InformationRequestTemplateDto =
        InformationRequestTemplateDtoMapper.toDto(
            definition = definition,
            draftVersion = versionRepository.findDraft(definition.id)?.let(projectionLoader::loadVersion),
            latestPublishedVersion = versionRepository.findLatestPublished(definition.id)
                ?.let(projectionLoader::loadVersion),
        )

    private fun toPublishedDto(definition: InformationRequestTemplateDefinition): InformationRequestTemplateDto =
        InformationRequestTemplateDtoMapper.toDto(
            definition = definition,
            draftVersion = null,
            latestPublishedVersion = versionRepository.findLatestPublished(definition.id)
                ?.let(projectionLoader::loadVersion),
        )

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestTemplateAuthoringService::class.java)

        /**
         * The audit target is a denormalized string rather than a resource type, because the
         * authorization stack has no entry for reusable request configuration.
         */
        const val TEMPLATE_TARGET_TYPE = "INFORMATION_REQUEST_TEMPLATE_DEFINITION"
        const val FIRST_VERSION_NUMBER = 1
    }
}
