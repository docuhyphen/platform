package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateOriginKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class CreateAdHocInformationRequestCommand(
    val exchangeId: UUID,
    val displayName: String,
    val description: String? = null,
    val configuration: InformationRequestTemplateConfigurationRequest,
    val gatesExchangeClosure: Boolean = true,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class InformationRequestCreationResult(
    val request: InformationRequest,
    val requestETag: String,
    val requirementCount: Int,
)

@ApplicationScoped
class InformationRequestAdHocCreationService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val capabilityRepository: InformationRequestTemplateVersionCapabilityRepository,
    private val requestRepository: InformationRequestRepository,
    private val configurationWriter: InformationRequestTemplateConfigurationWriter,
    private val schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
    private val materializer: InformationRequestTemplateMaterializer,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
)
{
    private val versionPublisher = InformationRequestPrivateVersionPublisher(
        definitionRepository,
        versionRepository,
        capabilityRepository,
        configurationWriter,
        schemaCompatibility,
    )

    @Transactional
    fun createAdHoc(command: CreateAdHocInformationRequestCommand): InformationRequestCreationResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.exchange(command.exchangeId),
            operation = CREATE_AD_HOC_OPERATION,
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

    private fun createMutation(command: CreateAdHocInformationRequestCommand): InformationRequestCreationResult
    {
        val exchange = exchangeRepository.findByIdForUpdate(command.exchangeId)
            ?: throw IllegalArgumentException("Exchange not found")
        if (exchange.isDeleted)
        {
            throw IllegalStateException("Information Requests cannot be created for a deleted Exchange")
        }
        requireDraftCreationAllowed(exchange)
        entitlementGuard.requireRequestMutation(exchange)
        authorize(command)

        val requestId = UUID.randomUUID()
        val definition = createDefinition(command, exchange, requestId)
        val version = createVersion(definition, command)
        val request = createRequest(command, exchange, version, requestId)
        recordDraftCreation(request, command)
        val materialized = materializer.materialize(
            request,
            FieldsAccessContext(command.access.principal, command.access.authorization),
        )
        return InformationRequestCreationResult(
            request = request,
            requestETag = InformationRequestETag.aggregateOf(request),
            requirementCount = materialized.requirementCount,
        )
    }

    private fun createDefinition(
        command: CreateAdHocInformationRequestCommand,
        exchange: Exchange,
        requestId: UUID,
    ): InformationRequestTemplateDefinition
    {
        val now = Timestamp.from(Instant.now())
        return definitionRepository.save(
            InformationRequestTemplateDefinition().apply {
                scopeKind = scopeKindOf(exchange)
                scopeOrgId = exchange.ownerOrganizationId
                scopeUserId = exchange.ownerUserId
                namespace = AD_HOC_NAMESPACE
                templateKey = "request-${requestId}"
                displayName = command.displayName.trim().ifBlank {
                    throw InformationRequestTemplateValidationException("displayName is required")
                }
                description = command.description?.trim()?.ifBlank { null }
                status = InformationRequestTemplateStatus.DRAFT
                originKind = InformationRequestTemplateOriginKind.AD_HOC_REQUEST
                originRequestId = requestId
                createdByAppUserId = command.access.principal.id.takeIf {
                    command.access.principal.kind == PrincipalKind.USER
                }
                createdAt = now
                updatedAt = now
            },
        )
    }

    private fun createVersion(
        definition: InformationRequestTemplateDefinition,
        command: CreateAdHocInformationRequestCommand,
    ): InformationRequestTemplateVersion =
        versionPublisher.publish(definition, FIRST_VERSION_NUMBER, command.configuration, command.access.principal)

    private fun createRequest(
        command: CreateAdHocInformationRequestCommand,
        exchange: Exchange,
        version: InformationRequestTemplateVersion,
        requestId: UUID,
    ): InformationRequest =
        requestRepository.save(
            InformationRequest().apply {
                id = requestId
                exchangeId = command.exchangeId
                templateVersionId = version.id
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

    private fun authorize(command: CreateAdHocInformationRequestCommand)
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
        command: CreateAdHocInformationRequestCommand,
    )
    {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = null,
                toState = InformationRequestState.DRAFT,
                mutation = InformationRequestMutation.CREATE_DRAFT,
                actor = command.access.principal,
                idempotencyKey = "information_request.draft.create|$CREATE_AD_HOC_OPERATION|${request.id}|" +
                    command.idempotencyKey,
            ),
        )
    }

    private fun scopeKindOf(exchange: Exchange): InformationRequestTemplateScopeKind =
        when
        {
            exchange.ownerOrganizationId != null -> InformationRequestTemplateScopeKind.ORGANIZATION
            exchange.ownerUserId != null -> InformationRequestTemplateScopeKind.PERSONAL
            else -> throw IllegalStateException("Exchange has no owner")
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

    private fun fingerprint(command: CreateAdHocInformationRequestCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                CREATE_AD_HOC_OPERATION,
                command.exchangeId.toString(),
                command.displayName.trim(),
                command.description?.trim().orEmpty(),
                command.gatesExchangeClosure.toString(),
                command.configuration.toString(),
            ).joinToString("|"),
        )

    private companion object
    {
        const val CREATE_AD_HOC_OPERATION = "create-ad-hoc-information-request"
        const val AD_HOC_NAMESPACE = "ad-hoc"
        const val FIRST_VERSION_NUMBER = 1
    }
}
