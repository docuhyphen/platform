package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementCurrent
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidenceAcceptedValue
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementGroup
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementCurrentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidenceAcceptedValueRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidencePolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementGroupRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class AddInformationRequestGroupOccurrenceCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val groupKey: String,
    val parentOccurrenceId: UUID? = null,
)

data class RemoveInformationRequestGroupOccurrenceCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val occurrenceId: UUID,
)

data class ReorderInformationRequestGroupOccurrencesCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val groupKey: String,
    val parentOccurrenceId: UUID? = null,
    val orderedOccurrenceIds: List<UUID>,
)

data class InformationRequestGroupOccurrenceResult(
    val request: InformationRequest,
    val responseETag: String,
    val occurrences: List<InformationRequestGroupOccurrence>,
)

@ApplicationScoped
class InformationRequestGroupOccurrenceService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val exchangeRepository: ExchangeRepository,
    private val groupRepository: InformationRequestTemplateRequirementGroupRepository,
    private val groupOccurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val templateBindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val dispositionRepository: InformationRequestTemplateBindingDispositionRepository,
    private val evidencePolicyRepository: InformationRequestTemplateEvidencePolicyRepository,
    private val acceptedValueRepository: InformationRequestTemplateEvidenceAcceptedValueRepository,
    private val substituteRepository: InformationRequestTemplateBindingSubstituteRepository,
    private val evidenceLinkRepository: InformationRequestTemplateBindingEvidenceLinkRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val currentRepository: InformationRequestRequirementCurrentRepository,
    private val schemaAssignmentRepository: SchemaAssignmentRepository,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
    private val executionGrantService: InformationRequestExecutionGrantService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val groupAuthorizationService: InformationRequestGroupAuthorizationService,
)
{
    @Transactional
    fun add(command: AddInformationRequestGroupOccurrenceCommand): InformationRequestGroupOccurrenceResult
        = runOnce(
            command.requestId,
            ADD_OPERATION,
            command.access,
            command.idempotencyKey,
            fingerprint(
                ADD_OPERATION,
                command.requestId,
                command.groupKey,
                command.parentOccurrenceId.orEmpty(),
            ),
        ) { addOccurrence(command) }

    @Transactional
    fun remove(command: RemoveInformationRequestGroupOccurrenceCommand): InformationRequestGroupOccurrenceResult
        = runOnce(
            command.requestId,
            REMOVE_OPERATION,
            command.access,
            command.idempotencyKey,
            fingerprint(REMOVE_OPERATION, command.requestId, command.occurrenceId),
        ) { removeOccurrence(command) }

    @Transactional
    fun reorder(command: ReorderInformationRequestGroupOccurrencesCommand): InformationRequestGroupOccurrenceResult
        = runOnce(
            command.requestId,
            REORDER_OPERATION,
            command.access,
            command.idempotencyKey,
            fingerprint(
                REORDER_OPERATION,
                command.requestId,
                command.groupKey,
                command.parentOccurrenceId.orEmpty(),
                command.orderedOccurrenceIds.joinToString(","),
            ),
        ) { reorderOccurrences(command) }

    private fun addOccurrence(
        command: AddInformationRequestGroupOccurrenceCommand,
    ): InformationRequestGroupOccurrenceResult = mutate(command.requestId, command.access, command.precondition) {
        request, _, now ->
        val template = templateData(request)
        val group = template.requireGroup(command.groupKey)
        val parent = requireParent(request.id, group, command.parentOccurrenceId, template.groupsById)
        val allSiblings = groupOccurrenceRepository.findForGroupAndParentForUpdate(
            request.id,
            group.id,
            command.parentOccurrenceId,
        )
        val activeSiblings = allSiblings.filter { it.removedAt == null }
        requireAddWithinMaximum(group, activeSiblings)
        groupAuthorizationService.authorizeMaterializedBindings(
            command.access,
            request,
            template.materializedBindings(group),
        )
        val nextDisplayIndex = activeSiblings.maxOfOrNull { it.occurrenceIndex }?.plus(1) ?: 0
        val nextIdentityIndex = nextOccurrenceIdentityIndex(group, parent, allSiblings)
        val occurrence = createOccurrence(
            request,
            group,
            parent,
            nextDisplayIndex,
            nextIdentityIndex,
            hasSchemaAssignment(request.id),
        )
        materializeAnchoredRequirements(request, template, group, occurrence, now)
        materializeChildMinimums(request, template, group, occurrence, now)
        val siblings = (activeSiblings + occurrence).sortedBy { it.occurrenceIndex }
        InformationRequestGroupOccurrenceResult(request, InformationRequestETag.responsesOf(request), siblings)
    }

    private fun removeOccurrence(
        command: RemoveInformationRequestGroupOccurrenceCommand,
    ): InformationRequestGroupOccurrenceResult = mutate(command.requestId, command.access, command.precondition) {
        request, _, now ->
        val template = templateData(request)
        val occurrence = groupOccurrenceRepository.findActiveByIdForUpdate(request.id, command.occurrenceId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request group occurrence not found",
            )
        val group = template.groupsById[occurrence.sourceTemplateGroupId]
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.STATE_INVALID,
                "Information Request group occurrence has no Template group",
            )
        val activeSiblings = groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
            request.id,
            group.id,
            occurrence.parentOccurrenceId,
        )
        if (activeSiblings.size <= group.minOccurrences)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_CARDINALITY_INVALID,
                "This group already has the minimum number of occurrences",
            )
        }
        val descendants = groupOccurrenceRepository.findActiveForRequestForUpdate(request.id)
            .filter { it.occurrencePath.startsWith("${occurrence.occurrencePath}/") }
        val removedScope = listOf(occurrence) + descendants
        groupAuthorizationService.authorizeOccurrenceScope(
            command.access,
            request.id,
            removedScope.map { it.occurrencePath }.toSet(),
        )
        removedScope.forEach {
            it.removedAt = now
            it.removedByPrincipalKind = command.access.principal.kind
            it.removedByPrincipalId = command.access.principal.id
            groupOccurrenceRepository.update(it)
        }
        val remaining = activeSiblings.filterNot { it.id == occurrence.id }
        InformationRequestGroupOccurrenceResult(request, InformationRequestETag.responsesOf(request), remaining)
    }

    private fun reorderOccurrences(
        command: ReorderInformationRequestGroupOccurrencesCommand,
    ): InformationRequestGroupOccurrenceResult = mutate(command.requestId, command.access, command.precondition) {
        request, _, _ ->
        val template = templateData(request)
        val group = template.requireGroup(command.groupKey)
        requireParent(request.id, group, command.parentOccurrenceId, template.groupsById)
        val activeSiblings = groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
            request.id,
            group.id,
            command.parentOccurrenceId,
        )
        if (activeSiblings.map { it.id }.toSet() != command.orderedOccurrenceIds.toSet() ||
            activeSiblings.size != command.orderedOccurrenceIds.size)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_ORDER_INVALID,
                "The occurrence order must name every active sibling exactly once",
            )
        }
        groupAuthorizationService.authorizeOccurrenceScope(
            command.access,
            request.id,
            activeSiblings.map { it.occurrencePath }.toSet(),
        )
        val byId = activeSiblings.associateBy { it.id }
        command.orderedOccurrenceIds.forEachIndexed { index, occurrenceId ->
            val occurrence = requireNotNull(byId[occurrenceId])
            if (occurrence.occurrenceIndex != index)
            {
                occurrence.occurrenceIndex = index
                groupOccurrenceRepository.update(occurrence)
            }
        }
        val ordered = command.orderedOccurrenceIds.map { requireNotNull(byId[it]) }
        InformationRequestGroupOccurrenceResult(request, InformationRequestETag.responsesOf(request), ordered)
    }

    private fun runOnce(
        requestId: UUID,
        operation: String,
        actor: RequestAccessContext,
        idempotencyKey: String,
        fingerprint: String,
        mutation: () -> InformationRequestGroupOccurrenceResult,
    ): InformationRequestGroupOccurrenceResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(requestId),
            operation = operation,
            actor = CommandActorRef.principal(actor.principal),
            idempotencyKey = idempotencyKey,
            requestFingerprint = fingerprint,
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = mutation()
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayGroupOccurrenceResult(decision.result, actor)
        }
    }

    private fun mutate(
        requestId: UUID,
        access: RequestAccessContext,
        precondition: CommandPrecondition,
        operation: (InformationRequest, Exchange, Timestamp) -> InformationRequestGroupOccurrenceResult,
    ): InformationRequestGroupOccurrenceResult
    {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeRepository)
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        precondition.requireSatisfiedBy(InformationRequestETag.responsesOf(request))
        authorize(access, request.id)
        requireResponseMutationAllowed(exchange, request)
        requireContinuationEntitlement(exchange, request)
        val now = Timestamp.from(Instant.now())
        val result = operation(request, exchange, now)
        request.responseRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.SAVE_RESPONSE,
                actor = access.principal,
                reasonCode = GROUP_OCCURRENCE_CHANGE_REASON,
                idempotencyKey = historyIdempotencyKey(request.id, result.responseETag),
            ),
        )
        return result.copy(responseETag = InformationRequestETag.responsesOf(request))
    }

    private fun requireResponseMutationAllowed(exchange: Exchange, request: InformationRequest)
    {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = exchange.status,
                deleted = exchange.isDeleted,
                lockedForUpdate = true,
            ),
            request.state,
            InformationRequestMutation.SAVE_RESPONSE,
        )
        if (decision is InformationRequestPolicyDecision.Deny)
        {
            throw InformationRequestLifecycleException(
                decision.reasonCode,
                "Information Request group occurrences cannot be changed now",
            )
        }
    }

    private fun requireContinuationEntitlement(exchange: Exchange, request: InformationRequest)
    {
        val grant = executionGrantService.findForRequest(request.id)
        if (grant == null)
        {
            entitlementGuard.requireRequestMutation(exchange)
        }
        else
        {
            entitlementGuard.requireNotOperationallySuspended(exchange)
            requireGrantNotRevoked(grant)
        }
    }

    private fun requireGrantNotRevoked(grant: RequestExecutionGrant)
    {
        if (grant.revokedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED,
                "This request's execution grant has been revoked",
            )
        }
    }

    private fun authorize(access: RequestAccessContext, requestId: UUID)
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Access denied to change Information Request group occurrences")
        }
    }

    private fun templateData(request: InformationRequest): TemplateData
    {
        val version = templateVersionRepository.findById(request.templateVersionId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.STATE_INVALID,
                "Information Request Template Version not found",
            )
        val groups = groupRepository.findForVersion(version.id)
        return TemplateData(
            templateDefinitionId = version.templateDefinitionId,
            groupsByKey = groups.associateBy { it.groupKey },
            groupsById = groups.associateBy { it.id },
            childrenByParent = groups.groupBy { it.parentGroupId },
            bindings = templateBindingRepository.findOrdered(version.id),
            requirementsById = templateRequirementRepository
                .findAllByDefinition(version.templateDefinitionId)
                .associateBy { it.id },
            configuration = loadConfiguration(version.id),
        )
    }

    private fun TemplateData.requireGroup(groupKey: String): InformationRequestTemplateRequirementGroup =
        groupsByKey[groupKey.trim()]
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request group not found",
            )

    private fun requireParent(
        requestId: UUID,
        group: InformationRequestTemplateRequirementGroup,
        parentOccurrenceId: UUID?,
        groupsById: Map<UUID, InformationRequestTemplateRequirementGroup>,
    ): InformationRequestGroupOccurrence?
    {
        if (group.parentGroupId == null)
        {
            if (parentOccurrenceId != null)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.GROUP_OCCURRENCE_PARENT_INVALID,
                    "A root group cannot name a parent occurrence",
                )
            }
            return null
        }
        val parent = parentOccurrenceId?.let { groupOccurrenceRepository.findActiveByIdForUpdate(requestId, it) }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_PARENT_INVALID,
                "A nested group occurrence requires an active parent occurrence",
            )
        if (parent.sourceTemplateGroupId != group.parentGroupId || groupsById[parent.sourceTemplateGroupId] == null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_PARENT_INVALID,
                "The parent occurrence does not match this group",
            )
        }
        return parent
    }

    private fun requireAddWithinMaximum(
        group: InformationRequestTemplateRequirementGroup,
        activeSiblings: List<InformationRequestGroupOccurrence>,
    )
    {
        val max = group.maxOccurrences ?: return
        if (activeSiblings.size >= max)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_CARDINALITY_INVALID,
                "This group already has the maximum number of occurrences",
            )
        }
    }

    private fun createOccurrence(
        request: InformationRequest,
        group: InformationRequestTemplateRequirementGroup,
        parent: InformationRequestGroupOccurrence?,
        displayIndex: Int,
        identityIndex: Int,
        provisionFieldValueSet: Boolean,
    ): InformationRequestGroupOccurrence
    {
        val path = groupOccurrencePath(group.groupKey, identityIndex, parent?.occurrencePath)
        val occurrence = groupOccurrenceRepository.save(
            InformationRequestGroupOccurrence().apply {
                informationRequestId = request.id
                sourceTemplateGroupId = group.id
                parentOccurrenceId = parent?.id
                occurrenceIndex = displayIndex
                occurrencePath = path
            },
        )
        if (provisionFieldValueSet)
        {
            schemaAssignmentService.createOccurrenceValueSet(
                FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id),
                path,
            )
        }
        return occurrence
    }

    private fun materializeChildMinimums(
        request: InformationRequest,
        template: TemplateData,
        parentGroup: InformationRequestTemplateRequirementGroup,
        parentOccurrence: InformationRequestGroupOccurrence,
        now: Timestamp,
    )
    {
        template.childrenByParent[parentGroup.id].orEmpty().forEach { child ->
            repeat(child.minOccurrences) { index ->
                val occurrence = createOccurrence(
                    request,
                    child,
                    parentOccurrence,
                    index,
                    index,
                    hasSchemaAssignment(request.id),
                )
                materializeAnchoredRequirements(request, template, child, occurrence, now)
                materializeChildMinimums(request, template, child, occurrence, now)
            }
        }
    }

    private fun TemplateData.materializedBindings(
        group: InformationRequestTemplateRequirementGroup,
    ): List<InformationRequestTemplateRequirementBinding> =
        bindings.filter { it.occurrenceAnchorKey == group.groupKey } +
            childrenByParent[group.id].orEmpty()
                .filter { it.minOccurrences > 0 }
                .flatMap { materializedBindings(it) }

    private fun materializeAnchoredRequirements(
        request: InformationRequest,
        template: TemplateData,
        group: InformationRequestTemplateRequirementGroup,
        occurrence: InformationRequestGroupOccurrence,
        now: Timestamp,
    )
    {
        template.bindings
            .filter { it.occurrenceAnchorKey == group.groupKey }
            .forEach { binding ->
                val requirement = template.requirementsById[binding.templateRequirementId]
                    ?: throw InformationRequestLifecycleException(
                        InformationRequestErrorCatalog.STATE_INVALID,
                        "Information Request Template Requirement not found",
                    )
                materializeRequirement(request, binding, requirement, template.configuration, now, occurrence.occurrencePath)
            }
    }

    private fun materializeRequirement(
        request: InformationRequest,
        binding: InformationRequestTemplateRequirementBinding,
        templateRequirement: InformationRequestTemplateRequirement,
        configuration: TemplateBindingConfiguration,
        now: Timestamp,
        occurrencePath: String,
    )
    {
        val requirement = requirementRepository.save(
            InformationRequestRequirement().apply {
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = templateRequirement.id
                sourceTemplateBindingId = binding.id
                this.occurrencePath = occurrencePath
            },
        )
        val revision = revisionRepository.save(
            InformationRequestRequirementRevision().apply {
                informationRequestRequirementId = requirement.id
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = templateRequirement.id
                sourceTemplateBindingId = binding.id
                revisionNumber = 1
                this.occurrencePath = occurrencePath
                effectiveFrom = now
                configurationHashSha256 = configurationHash(binding, templateRequirement, configuration)
                optimisticVersion = 1
            },
        )
        currentRepository.save(
            InformationRequestRequirementCurrent().apply {
                informationRequestRequirementId = requirement.id
                currentRevisionId = revision.id
                currentRevisionNumber = 1
                updatedAt = now
            },
        )
    }

    private fun hasSchemaAssignment(requestId: UUID): Boolean =
        schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, requestId) != null

    private fun loadConfiguration(templateVersionId: UUID) = TemplateBindingConfiguration(
        dispositionsByBinding = dispositionRepository.findForVersion(templateVersionId)
            .groupBy { it.templateBindingId },
        policiesByBinding = evidencePolicyRepository.findForVersion(templateVersionId)
            .associateBy { it.templateBindingId },
        acceptedValuesByPolicy = acceptedValueRepository.findForVersion(templateVersionId)
            .groupBy { it.evidencePolicyId },
        substitutesByBinding = substituteRepository.findForVersion(templateVersionId)
            .groupBy { it.templateBindingId },
        evidenceLinksByBinding = evidenceLinkRepository.findForVersion(templateVersionId)
            .groupBy { it.templateBindingId },
    )

    private fun configurationHash(
        binding: InformationRequestTemplateRequirementBinding,
        requirement: InformationRequestTemplateRequirement,
        configuration: TemplateBindingConfiguration,
    ): String
    {
        val policy = configuration.policiesByBinding[binding.id]
        val material = listOf(
            "binding:${binding.id}",
            "requirement:${requirement.id}",
            "requirementKey:${requirement.requirementKey}",
            "requirementType:${requirement.requirementType}",
            "prompt:${binding.prompt}",
            "helpText:${binding.helpText.orEmpty()}",
            "responseMode:${binding.responseMode}",
            "requiredness:${binding.requiredness}",
            "contributorRole:${binding.contributorRole}",
            "reviewPolicy:${binding.reviewPolicy}",
            "confidentiality:${binding.confidentialityCompartmentKey.orEmpty()}",
            "condition:${binding.conditionalRuleKey.orEmpty()}",
            "occurrence:${binding.occurrenceAnchorKey.orEmpty()}",
            "collectedField:${binding.collectedFieldDefinitionId?.toString().orEmpty()}",
            "dispositions:${configuration.dispositionsByBinding[binding.id].orEmpty().map { it.disposition.name }}",
            "policy:${policy?.stableHashMaterial().orEmpty()}",
            "accepted:${policy?.let { acceptedHashMaterial(it, configuration) }.orEmpty()}",
            "substitutes:${configuration.substitutesByBinding[binding.id].orEmpty().map { it.substituteTemplateBindingId }}",
            "supporting:${configuration.evidenceLinksByBinding[binding.id].orEmpty().map { it.supportingTemplateBindingId }}",
        ).joinToString(separator = "\n")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun acceptedHashMaterial(
        policy: InformationRequestTemplateEvidencePolicy,
        configuration: TemplateBindingConfiguration,
    ): List<String> = configuration.acceptedValuesByPolicy[policy.id]
        .orEmpty()
        .map { "${it.attribute}:${it.acceptedValue}" }

    private fun InformationRequestTemplateEvidencePolicy.stableHashMaterial(): String = listOf(
        minimumFileCount,
        maximumFileCount,
        maximumFileSizeBytes,
        maximumTotalSizeBytes,
        minimumPageCount,
        maximumPageCount,
        issuerRequirement,
        jurisdictionRequirement,
        languageRequirement,
        issueDateRequirement,
        expiryDateRequirement,
        coveragePeriodRequirement,
        certificationRequirement,
        signatureRequirement,
        maximumIssueAgeDays,
        minimumRemainingValidityDays,
        minimumCoverageDays,
        coverageContinuityRequired,
        waiverPolicy,
        conformancePolicy,
    ).joinToString(separator = "|")

    private fun groupOccurrencePath(groupKey: String, index: Int, parentPath: String?): String =
        (parentPath?.let { "$it/" } ?: "") + "$groupKey[$index]"

    private fun nextOccurrenceIdentityIndex(
        group: InformationRequestTemplateRequirementGroup,
        parent: InformationRequestGroupOccurrence?,
        siblings: List<InformationRequestGroupOccurrence>,
    ): Int = siblings
        .mapNotNull { occurrenceIdentityIndex(group.groupKey, parent?.occurrencePath, it.occurrencePath) }
        .maxOrNull()
        ?.plus(1)
        ?: 0

    private fun occurrenceIdentityIndex(groupKey: String, parentPath: String?, occurrencePath: String): Int?
    {
        val prefix = (parentPath?.let { "$it/" } ?: "") + "$groupKey["
        if (!occurrencePath.startsWith(prefix) || !occurrencePath.endsWith("]"))
            return null
        return occurrencePath
            .substring(prefix.length, occurrencePath.length - 1)
            .toIntOrNull()
    }

    private fun replayGroupOccurrenceResult(result: CommandResultReference, access: RequestAccessContext): InformationRequestGroupOccurrenceResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST) {
            "Command receipt does not reference an Information Request"
        }
        val request = requestRepository.findById(result.resourceId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request receipt target not found",
            )
        InformationRequestReadAuthorization.requireView(authorizationService, request.id, access)
        return InformationRequestGroupOccurrenceResult(
            request = request,
            responseETag = requireNotNull(result.etag) {
                "Information Request group occurrence receipt did not record a response ETag"
            },
            occurrences = groupOccurrenceRepository.findForRequest(request.id),
        )
    }

    private fun InformationRequestGroupOccurrenceResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            revision = request.responseRevision,
            etag = responseETag,
        )

    private fun fingerprint(operation: String, vararg fields: Any): String =
        CommandRequestFingerprint.sha256Hex((listOf(operation) + fields.map { it.toString() }).joinToString("|"))

    private fun UUID?.orEmpty(): String = this?.toString().orEmpty()

    private fun historyIdempotencyKey(requestId: UUID, responseETag: String): String =
        "information_request.group_occurrence|$requestId|$responseETag"

    private data class TemplateData(
        val templateDefinitionId: UUID,
        val groupsByKey: Map<String, InformationRequestTemplateRequirementGroup>,
        val groupsById: Map<UUID, InformationRequestTemplateRequirementGroup>,
        val childrenByParent: Map<UUID?, List<InformationRequestTemplateRequirementGroup>>,
        val bindings: List<InformationRequestTemplateRequirementBinding>,
        val requirementsById: Map<UUID, InformationRequestTemplateRequirement>,
        val configuration: TemplateBindingConfiguration,
    )

    private data class TemplateBindingConfiguration(
        val dispositionsByBinding: Map<UUID, List<InformationRequestTemplateBindingDisposition>>,
        val policiesByBinding: Map<UUID, InformationRequestTemplateEvidencePolicy>,
        val acceptedValuesByPolicy: Map<UUID, List<InformationRequestTemplateEvidenceAcceptedValue>>,
        val substitutesByBinding: Map<UUID, List<InformationRequestTemplateBindingSubstitute>>,
        val evidenceLinksByBinding: Map<UUID, List<InformationRequestTemplateBindingEvidenceLink>>,
    )

    private companion object
    {
        const val ADD_OPERATION = "add-information-request-group-occurrence"
        const val REMOVE_OPERATION = "remove-information-request-group-occurrence"
        const val REORDER_OPERATION = "reorder-information-request-group-occurrences"
        const val GROUP_OCCURRENCE_CHANGE_REASON = "GROUP_OCCURRENCES_CHANGED"
    }
}
