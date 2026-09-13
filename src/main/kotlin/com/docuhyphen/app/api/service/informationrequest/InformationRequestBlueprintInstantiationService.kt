package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.BlueprintFieldDefault
import com.docuhyphen.app.api.model.entity.BlueprintParticipantDefault
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestDocumentPlaceholder
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestDocumentPlaceholderRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.blueprint.BlueprintDocumentInstantiationDefault
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.fields.BlueprintFieldDefaultEntry
import com.docuhyphen.app.api.service.fields.BlueprintFieldDefaultsCommand
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsJson
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class CreateInformationRequestFromBlueprintCommand(
    val blueprintDefinitionId: UUID,
    val exchangeId: UUID,
    val gatesExchangeClosure: Boolean = true,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class BlueprintInformationRequestPartyDefault(
    val roleKey: InformationRequestShareRoleKey,
    val principal: PrincipalRef,
)

@ApplicationScoped
class InformationRequestBlueprintInstantiationService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val requestRepository: InformationRequestRepository,
    private val placeholderRepository: InformationRequestDocumentPlaceholderRepository,
    private val blueprintService: BlueprintDefinitionService,
    private val partyService: InformationRequestPartyService,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val materializer: InformationRequestTemplateMaterializer,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
)
{
    @Transactional
    fun createFromBlueprint(command: CreateInformationRequestFromBlueprintCommand): InformationRequestCreationResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.blueprint(command.blueprintDefinitionId),
            operation = CREATE_FROM_BLUEPRINT_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = fingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = createMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayCreationResult(decision.result, command.access)
        }
    }

    private fun createMutation(command: CreateInformationRequestFromBlueprintCommand): InformationRequestCreationResult
    {
        val exchange = exchangeRepository.findByIdForUpdate(command.exchangeId)
            ?: throw IllegalArgumentException("Exchange not found")
        if (exchange.isDeleted)
        {
            throw IllegalStateException("Information Requests cannot be created for a deleted Exchange")
        }
        requireDraftCreationAllowed(exchange)
        authorize(command)

        val snapshot = blueprintService.loadInformationRequestInstantiationSnapshot(
            command.blueprintDefinitionId,
            command.access.principal,
            command.access.authorization,
        )
        val request = requestRepository.save(
            InformationRequest().apply {
                exchangeId = command.exchangeId
                templateVersionId = snapshot.templateVersionId
                ownerType = ownerTypeOf(exchange)
                ownerOrganizationId = exchange.ownerOrganizationId
                ownerUserId = exchange.ownerUserId
                state = InformationRequestState.DRAFT
                gatesExchangeClosure = command.gatesExchangeClosure
                createdByAppUserId = command.access.principal.id.takeIf {
                    command.access.principal.kind == PrincipalKind.USER
                }
                val now = Timestamp.from(Instant.now())
                createdAt = now
                updatedAt = now
            },
        )
        recordDraftCreation(request, command)
        val materialized = materializer.materialize(
            request,
            FieldsAccessContext(command.access.principal, command.access.authorization),
        )
        partyService.materializeBlueprintDefaultParties(
            request,
            snapshot.participantDefaults.map(::mapParticipantDefault),
            command.access,
        )
        materializeDocumentPlaceholders(request, snapshot.documentDefaults)
        applyFieldDefaults(request, snapshot.fieldDefaults, command.access)
        return InformationRequestCreationResult(
            request = request,
            requestETag = InformationRequestETag.aggregateOf(request),
            requirementCount = materialized.requirementCount,
        )
    }

    private fun applyFieldDefaults(
        request: InformationRequest,
        defaults: List<BlueprintFieldDefault>,
        access: RequestAccessContext,
    )
    {
        val entries = defaults.mapNotNull { default ->
            val raw = default.valueJson ?: return@mapNotNull null
            BlueprintFieldDefaultEntry(default.fieldDefinitionId, FieldsJson.instance.parseToJsonElement(raw))
        }
        if (entries.isEmpty()) return
        schemaAssignmentService.applyBlueprintFieldDefaults(
            BlueprintFieldDefaultsCommand(
                resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id),
                access = FieldsAccessContext(access.principal, access.authorization),
                defaults = entries,
            ),
        )
    }

    private fun materializeDocumentPlaceholders(
        request: InformationRequest,
        defaults: List<BlueprintDocumentInstantiationDefault>,
    )
    {
        defaults.forEach { source ->
            val library = source.libraryEntry
            placeholderRepository.save(
                InformationRequestDocumentPlaceholder().apply {
                    informationRequestId = request.id
                    sourceBlueprintDocumentDefaultId = source.default.id
                    title = source.default.title
                    restrictedType = source.default.restrictedType
                    restrictType = source.default.restrictType
                    required = source.default.required
                    libraryDocumentId = source.default.libraryDocumentId
                    libraryTitle = library?.title
                    libraryDescription = library?.description
                    libraryDocumentType = library?.documentType
                    libraryFileName = library?.fileName
                    libraryFileSizeBytes = library?.fileSizeBytes
                    libraryContentHash = library?.contentHash
                    displayOrder = source.default.displayOrder
                },
            )
        }
    }

    private fun mapParticipantDefault(default: BlueprintParticipantDefault): BlueprintInformationRequestPartyDefault =
        BlueprintInformationRequestPartyDefault(
            roleKey = when (default.roleName)
            {
                ExchangeShareRoleName.REVIEWER -> InformationRequestShareRoleKey.REVIEWER
                ExchangeShareRoleName.SIGNER -> InformationRequestShareRoleKey.ATTESTOR
                ExchangeShareRoleName.EDITOR -> InformationRequestShareRoleKey.PREPARER
                ExchangeShareRoleName.OWNER -> InformationRequestShareRoleKey.DECISION_MAKER
                ExchangeShareRoleName.VIEWER,
                ExchangeShareRoleName.COMMENTER,
                ExchangeShareRoleName.PARTICIPANT,
                -> InformationRequestShareRoleKey.CONTRIBUTOR
            },
            principal = when (default.principalKind.trim().uppercase())
            {
                "APP_USER", "USER" -> PrincipalRef.user(UUID.fromString(default.principalId))
                "PRINCIPAL_GROUP" -> PrincipalRef.group(UUID.fromString(default.principalId))
                else -> throw IllegalArgumentException(
                    "Blueprint participant default ${default.id} names an unsupported principal kind",
                )
            },
        )

    private fun authorize(command: CreateInformationRequestFromBlueprintCommand)
    {
        val decision = authorizationService.authorize(
            command.access.principal,
            Action.INFORMATION_REQUEST_CREATE,
            ResourceRef.exchange(command.exchangeId),
            command.access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Access denied to create Information Requests")
        }
    }

    private fun requireDraftCreationAllowed(exchange: Exchange)
    {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = exchange.status,
                deleted = exchange.isDeleted,
                lockedForUpdate = true,
            ),
            null,
            InformationRequestMutation.CREATE_DRAFT,
        )
        if (decision is InformationRequestPolicyDecision.Deny)
        {
            throw InformationRequestLifecycleException(
                decision.reasonCode,
                "Information Request draft creation is not allowed",
            )
        }
    }

    private fun recordDraftCreation(
        request: InformationRequest,
        command: CreateInformationRequestFromBlueprintCommand,
    )
    {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = null,
                toState = InformationRequestState.DRAFT,
                mutation = InformationRequestMutation.CREATE_DRAFT,
                actor = command.access.principal,
                idempotencyKey = "information_request.draft.create|$CREATE_FROM_BLUEPRINT_OPERATION|${request.id}|" +
                    command.idempotencyKey,
            ),
        )
    }

    private fun ownerTypeOf(exchange: Exchange): InformationRequestOwnerType =
        when
        {
            exchange.ownerOrganizationId != null -> InformationRequestOwnerType.ORGANIZATION
            exchange.ownerUserId != null -> InformationRequestOwnerType.USER
            else -> throw IllegalStateException("Exchange has no owner")
        }

    private fun replayCreationResult(result: CommandResultReference, access: RequestAccessContext): InformationRequestCreationResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST) {
            "Command receipt does not reference an Information Request"
        }
        val request = requestRepository.findById(result.resourceId)
            ?: throw IllegalArgumentException("Information Request receipt target not found")
        InformationRequestReadAuthorization.requireView(authorizationService, request.id, access)
        return InformationRequestCreationResult(
            request = request,
            requestETag = requireNotNull(result.etag) {
                "Information Request creation receipt did not record an aggregate ETag"
            },
            requirementCount = requireNotNull(result.revision) {
                "Information Request creation receipt did not record materialized Requirements"
            }.toInt(),
        )
    }

    private fun InformationRequestCreationResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            revision = requirementCount.toLong(),
            etag = requestETag,
        )

    private fun fingerprint(command: CreateInformationRequestFromBlueprintCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                CREATE_FROM_BLUEPRINT_OPERATION,
                command.blueprintDefinitionId.toString(),
                command.exchangeId.toString(),
                command.gatesExchangeClosure.toString(),
            ).joinToString("|"),
        )

    private companion object
    {
        const val CREATE_FROM_BLUEPRINT_OPERATION = "create-information-request-from-blueprint"
    }
}
