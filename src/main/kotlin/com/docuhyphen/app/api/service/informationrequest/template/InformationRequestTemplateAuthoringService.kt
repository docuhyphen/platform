package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestTemplateDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandMutationResult
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptDecision
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateMutationContext
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionContent
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditTargets
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
    private val actorLabels: InformationRequestAuditActorLabelResolver,
    private val entitlementGuard: InformationRequestTemplateEntitlementGuard,
    private val schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
    private val commandReceiptService: CommandReceiptService,
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
        templateView(definition)

    internal fun currentAuthor(): PrincipalRef = requireUserPrincipal()

    fun listTemplates(
        scopeKind: InformationRequestTemplateScopeKind? = null,
    ): List<InformationRequestTemplateSummaryDto>
    {
        val principal = requireUserPrincipal()
        val resolved = scopeKind ?: defaultScopeKind()
        val (definitions, editable) = when (resolved)
        {
            InformationRequestTemplateScopeKind.ORGANIZATION ->
            {
                val organizationId = requireSelectedOrganization(
                    principal, Action.INFORMATION_REQUEST_TEMPLATE_VIEW,
                )
                entitlementGuard.requireTemplateAccess(resolved, organizationId, null, null)
                definitionRepository.findAllForOrganization(organizationId) to
                        hasOrganizationAccess(principal, organizationId, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)
            }

            InformationRequestTemplateScopeKind.PERSONAL ->
            {
                entitlementGuard.requireTemplateAccess(
                    resolved, null, principal.id, entitlementOrganizationId(resolved),
                )
                definitionRepository.findAllForUser(principal.id) to true
            }

            InformationRequestTemplateScopeKind.PLATFORM ->
                definitionRepository.findAllPlatform() to userRoleService.isAppAdmin(principal.id)
        }

        val versionsByDefinition = versionRepository
            .findForDefinitions(definitions.map { it.id })
            .groupBy { it.templateDefinitionId }
            .mapValues { (_, versions) ->
                versions.filter { editable || it.status != InformationRequestTemplateStatus.DRAFT }
            }

        return definitions
            .filter { editable || versionsByDefinition[it.id].orEmpty().isNotEmpty() }
            .map { definition ->
                InformationRequestTemplateDtoMapper.toSummaryDto(
                    definition = definition,
                    versions = versionsByDefinition[definition.id].orEmpty(),
                )
            }
    }

    fun getTemplate(id: UUID): InformationRequestTemplateDto
    {
        val definition = requireDefinition(id)
        val principal = requireOwnerAccess(definition, Action.INFORMATION_REQUEST_TEMPLATE_VIEW)
        if (definition.scopeKind != InformationRequestTemplateScopeKind.PLATFORM) {
            entitlementGuard.requireTemplateAccess(
                definition.scopeKind, definition.scopeOrgId, definition.scopeUserId,
                entitlementOrganizationId(definition.scopeKind),
            )
        }
        if (mayEdit(definition, principal)) return templateView(definition)
        val versions = versionRepository.findForDefinitions(listOf(definition.id))
        val published = versionRepository.findLatestPublished(definition.id)
        if (published == null && versions.none { it.status == InformationRequestTemplateStatus.RETIRED }) {
            throw ForbiddenException(ACCESS_REFUSAL)
        }
        return publishedTemplateView(definition, published, versions)
    }

    fun getVersion(id: UUID, versionNumber: Int): InformationRequestTemplateVersionDto {
        val definition = requireDefinition(id)
        val principal = requireOwnerAccess(definition, Action.INFORMATION_REQUEST_TEMPLATE_VIEW)
        if (definition.scopeKind != InformationRequestTemplateScopeKind.PLATFORM)
        {
            entitlementGuard.requireTemplateAccess(
                definition.scopeKind, definition.scopeOrgId, definition.scopeUserId,
                entitlementOrganizationId(definition.scopeKind),
            )
        }
        val version = versionRepository.findByNumber(definition.id, versionNumber)
            ?.takeIf { it.status != InformationRequestTemplateStatus.DRAFT || mayEdit(definition, principal) }
            ?: throw IllegalArgumentException("Information request template version not found: $versionNumber")
        return projectionLoader.loadVersion(version)
    }

    fun frozenVersionContents(
        access: RequestAccessContext,
        templateKeys: Collection<String>,
    ): List<InformationRequestTemplateVersionContent> {
        val principal = access.principal.takeIf { it.kind == PrincipalKind.USER } ?: return emptyList()
        if (templateKeys.isEmpty()) return emptyList()
        val organizationId = access.authorization.activeOrgId
        val definitions = buildList {
            if (organizationId != null &&
                hasOrganizationAccess(
                    principal,
                    organizationId,
                    Action.INFORMATION_REQUEST_TEMPLATE_VIEW,
                    access.authorization
                ) &&
                entitled(InformationRequestTemplateScopeKind.ORGANIZATION, organizationId, null, null)
            ) {
                addAll(definitionRepository.findAllForOrganization(organizationId))
            }
            if (entitled(InformationRequestTemplateScopeKind.PERSONAL, null, principal.id, organizationId)) {
                addAll(definitionRepository.findAllForUser(principal.id))
            }
            addAll(definitionRepository.findAllPlatform())
        }.filter { it.templateKey in templateKeys }
        if (definitions.isEmpty()) return emptyList()

        val keyByDefinition = definitions.associate { it.id to it.templateKey }
        return versionRepository.findForDefinitions(definitions.map { it.id })
            .filter { it.status != InformationRequestTemplateStatus.DRAFT }
            .mapNotNull { version ->
                projectionLoader.loadVersion(version).contentHashSha256?.let { hash ->
                    InformationRequestTemplateVersionContent(
                        templateKey = keyByDefinition.getValue(version.templateDefinitionId),
                        versionNumber = version.versionNumber,
                        contentHashSha256 = hash,
                    )
                }
            }
    }

    @Transactional
    fun createTemplate(
        request: CreateInformationRequestTemplateRequest,
        idempotencyKey: String,
    ): InformationRequestTemplateDto {
        val principal = requireUserPrincipal()
        val scopeKind = request.scopeKind ?: defaultScopeKind()
        val receipt = InformationRequestTemplateCommandReceipts.forOwner(
            scopeKind,
            when (scopeKind) {
                InformationRequestTemplateScopeKind.ORGANIZATION -> currentContext().activeOrgId
                InformationRequestTemplateScopeKind.PERSONAL -> principal.id
                InformationRequestTemplateScopeKind.PLATFORM -> null
            },
            InformationRequestTemplateCommandReceipts.CREATE,
            principal,
            idempotencyKey,
            scopeKind,
            request.namespace.trim().lowercase(),
            request.templateKey.trim().lowercase(),
            request.displayName.trim(),
            request.description?.trim().orEmpty(),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val definition = createDefinition(request)
                CommandMutationResult(
                    templateView(definition),
                    InformationRequestTemplateCommandReceipts.result(definition.id)
                )
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> getTemplate(decision.result.resourceId)
        }
    }

    internal fun createDefinition(request: CreateInformationRequestTemplateRequest): InformationRequestTemplateDefinition
    {
        val principal = requireUserPrincipal()
        val scopeKind = request.scopeKind ?: defaultScopeKind()
        if (scopeKind == InformationRequestTemplateScopeKind.PLATFORM)
        {
            requirePlatformAuthor(principal)
        }
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
        InformationRequestTemplateTextLimits.requireDefinitionWithinLimits(
            namespace,
            templateKey,
            displayName,
            request.description?.trim(),
        )

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
                this.createdByPrincipalKind = principal.kind
                this.createdByPrincipalId = principal.id
            },
        )
        versionRepository.save(
            InformationRequestTemplateVersion().apply {
                templateDefinitionId = definition.id
                versionNumber = FIRST_VERSION_NUMBER
                createdByPrincipalKind = principal.kind
                createdByPrincipalId = principal.id
            },
        )

        recordTemplateEvent(AuditEventType.INFORMATION_REQUEST_TEMPLATE_CREATE, definition, principal)
        return definition
    }

    @Transactional
    fun replaceDraftConfiguration(
        id: UUID,
        request: InformationRequestTemplateConfigurationRequest,
        precondition: CommandPrecondition,
    ): InformationRequestTemplateDto = configureDraft(id, request, precondition, source = null)

    internal fun configureCopiedDraft(
        id: UUID,
        request: InformationRequestTemplateConfigurationRequest,
        source: InformationRequestTemplateVersion,
    ): InformationRequestTemplateDto = configureDraft(id, request, CommandPrecondition.Unconditioned, source)

    private fun configureDraft(
        id: UUID,
        request: InformationRequestTemplateConfigurationRequest,
        precondition: CommandPrecondition,
        source: InformationRequestTemplateVersion?,
    ): InformationRequestTemplateDto
    {
        val mutation = requireMutationContext(id)
        val definition = definitionRepository.findByIdForUpdate(mutation.definition.id)
            ?: throw IllegalArgumentException("Information request template not found: $id")

        schemaCompatibility.requireUsable(definition, request.schemaVersionId)

        val draft = versionRepository.findDraftForUpdate(definition.id)
            ?: throw IllegalStateException(
                "Information request template $id has no editable version",
            )
        precondition.requireSatisfiedBy(InformationRequestTemplateETag.draftOf(draft))
        draft.revision += 1
        configurationWriter.replaceConfiguration(draft, request)

        definition.updatedAt = Timestamp.from(Instant.now())
        definitionRepository.update(definition)

        recordTemplateVersionEvent(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_CONFIGURE,
            definition,
            draft,
            mutation.principal,
            source,
        )
        return templateView(definition)
    }

    private fun defaultScopeKind(): InformationRequestTemplateScopeKind =
        if (currentContext().activeOrgId != null)
            InformationRequestTemplateScopeKind.ORGANIZATION
        else
            InformationRequestTemplateScopeKind.PERSONAL

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

            InformationRequestTemplateScopeKind.PERSONAL ->
            {
                if (definition.scopeUserId == principal.id)
                {
                    return principal
                }
            }

            InformationRequestTemplateScopeKind.PLATFORM -> {
                if (action == Action.INFORMATION_REQUEST_TEMPLATE_VIEW || userRoleService.isAppAdmin(principal.id)) {
                    return principal
                }
            }
        }
        throw ForbiddenException(ACCESS_REFUSAL)
    }

    private fun mayEdit(definition: InformationRequestTemplateDefinition, principal: PrincipalRef): Boolean =
        when (definition.scopeKind) {
            InformationRequestTemplateScopeKind.ORGANIZATION -> {
                val organizationId = definition.scopeOrgId
                organizationId != null &&
                        currentContext().activeOrgId == organizationId &&
                        hasOrganizationAccess(principal, organizationId, Action.INFORMATION_REQUEST_TEMPLATE_EDIT)
            }

            InformationRequestTemplateScopeKind.PERSONAL -> definition.scopeUserId == principal.id
            InformationRequestTemplateScopeKind.PLATFORM -> userRoleService.isAppAdmin(principal.id)
        }

    private fun requirePlatformAuthor(principal: PrincipalRef) {
        if (!userRoleService.isAppAdmin(principal.id)) {
            throw ForbiddenException("Platform Templates are authored by platform administrators")
        }
    }

    private fun requireSelectedOrganization(principal: PrincipalRef, action: Action): UUID
    {
        val organizationId = currentContext().activeOrgId
            ?: throw ForbiddenException("An active organization is required")
        if (!hasOrganizationAccess(principal, organizationId, action))
        {
            throw ForbiddenException(ACCESS_REFUSAL)
        }
        return organizationId
    }

    private fun hasOrganizationAccess(
        principal: PrincipalRef,
        organizationId: UUID,
        action: Action,
        context: AuthorizationContext = currentContext(),
    ): Boolean
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
            context,
        ) is Decision.Allow
    }

    private fun entitled(
        scopeKind: InformationRequestTemplateScopeKind,
        organizationId: UUID?,
        userId: UUID?,
        actingOrganizationId: UUID?,
    ): Boolean =
        try {
            entitlementGuard.requireTemplateAccess(scopeKind, organizationId, userId, actingOrganizationId)
            true
        } catch (_: SubscriptionDenialException) {
            false
        }

    private fun requireDefinition(id: UUID): InformationRequestTemplateDefinition =
        definitionRepository.findById(id)
            ?.takeIf { it.originKind == InformationRequestTemplateOriginKind.REUSABLE }
            ?: throw ForbiddenException(ACCESS_REFUSAL)

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

    internal fun recordTemplateVersionEvent(
        eventType: AuditEventType,
        definition: InformationRequestTemplateDefinition,
        version: InformationRequestTemplateVersion,
        principal: PrincipalRef,
        source: InformationRequestTemplateVersion?,
    ) = recordTemplateEvent(
        eventType,
        definition,
        principal,
        buildMap {
            put("templateVersionId", version.id.toString())
            put("versionNumber", version.versionNumber.toString())
            put("versionRevision", version.revision.toString())
            source?.let {
                put("sourceTemplateDefinitionId", it.templateDefinitionId.toString())
                put("sourceTemplateVersionId", it.id.toString())
                put("sourceVersionNumber", it.versionNumber.toString())
            }
        },
    )

    internal fun recordTemplateEvent(
        eventType: AuditEventType,
        definition: InformationRequestTemplateDefinition,
        principal: PrincipalRef,
        versionDetails: Map<String, String> = emptyMap(),
    )
    {
        val actor = InformationRequestAuditActor.of(RequestAccessContext(principal, currentContext()))
        val attribution = actorLabels.attribute(actor)
        auditRecorder.record(
            AuditEventDraft(
                owner = auditOwnerOf(definition),
                eventTypeKey = eventType.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                targetType = InformationRequestAuditTargets.TEMPLATE_DEFINITION,
                targetId = definition.id.toString(),
                targetLabel = definition.displayName,
                payload = mapOf(
                    "namespace" to definition.namespace,
                    "templateKey" to definition.templateKey,
                    "scopeKind" to definition.scopeKind.name,
                ) + versionDetails,
            ),
        )
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

    private fun templateView(definition: InformationRequestTemplateDefinition): InformationRequestTemplateDto {
        val draft = versionRepository.findDraft(definition.id)
        return InformationRequestTemplateDtoMapper.toDto(
            definition = definition,
            draftVersion = draft?.let(projectionLoader::loadVersion),
            latestPublishedVersion = versionRepository.findLatestPublished(definition.id)
                ?.let(projectionLoader::loadVersion),
            draftETag = draft?.let(InformationRequestTemplateETag::draftOf),
            versions = versionRepository.findForDefinitions(listOf(definition.id)),
        )
    }

    private fun publishedTemplateView(
        definition: InformationRequestTemplateDefinition,
        published: InformationRequestTemplateVersion?,
        versions: List<InformationRequestTemplateVersion>,
    ): InformationRequestTemplateDto =
        InformationRequestTemplateDtoMapper.toDto(
            definition = definition,
            draftVersion = null,
            latestPublishedVersion = published?.let(projectionLoader::loadVersion),
            versions = versions,
        )

    private companion object
    {
        const val FIRST_VERSION_NUMBER = 1
        const val ACCESS_REFUSAL = "Access denied to information request template configuration"
    }
}
