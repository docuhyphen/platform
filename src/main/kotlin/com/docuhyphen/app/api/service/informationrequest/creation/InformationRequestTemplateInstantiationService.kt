package com.docuhyphen.app.api.service.informationrequest.creation

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromTemplateVersionCommand
import com.docuhyphen.app.api.model.informationrequest.creation.InformationRequestCreationResult
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionReference
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
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
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateReferenceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class InformationRequestTemplateInstantiationService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val requestRepository: InformationRequestRepository,
    private val templateReferenceService: InformationRequestTemplateReferenceService,
    private val materializer: InformationRequestTemplateMaterializer,
    private val entitlementGuard: InformationRequestEntitlementGuard,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
)
{
    @Transactional
    fun createFromTemplateVersion(command: CreateInformationRequestFromTemplateVersionCommand): InformationRequestCreationResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.exchange(command.exchangeId),
            operation = CREATE_FROM_TEMPLATE_VERSION_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                listOf(
                    CREATE_FROM_TEMPLATE_VERSION_OPERATION,
                    command.templateVersionId.toString(),
                    command.exchangeId.toString(),
                    command.gatesExchangeClosure.toString(),
                ).joinToString("|"),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = createMutation(command)
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        resourceType = ResourceType.INFORMATION_REQUEST,
                        resourceId = result.request.id,
                        revision = result.requirementCount.toLong(),
                        etag = result.requestETag,
                    ),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(decision.result, command.access)
        }
    }

    private fun createMutation(command: CreateInformationRequestFromTemplateVersionCommand): InformationRequestCreationResult
    {
        val exchange = exchangeRepository.findByIdForUpdate(command.exchangeId)
            ?: throw IllegalArgumentException("Exchange not found")
        if (exchange.isDeleted)
        {
            throw IllegalStateException("Information Requests cannot be created for a deleted Exchange")
        }
        requireDraftCreationAllowed(exchange)
        entitlementGuard.requireRequestCreation(exchange)
        authorize(command)
        val reference = templateReferenceService.requireInstantiableVersion(command.templateVersionId)
        requireHeldForOwner(reference, exchange)

        val now = Timestamp.from(Instant.now())
        val request = requestRepository.save(
            InformationRequest().apply {
                exchangeId = command.exchangeId
                templateVersionId = reference.templateVersionId
                ownerType = if (exchange.ownerOrganizationId != null)
                    InformationRequestOwnerType.ORGANIZATION
                else
                    InformationRequestOwnerType.USER
                ownerOrganizationId = exchange.ownerOrganizationId
                ownerUserId = exchange.ownerUserId
                state = InformationRequestState.DRAFT
                gatesExchangeClosure = command.gatesExchangeClosure
                createdByAppUserId = command.access.principal.id.takeIf {
                    command.access.principal.kind == PrincipalKind.USER
                }
                createdAt = now
                updatedAt = now
            },
        )
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = null,
                toState = InformationRequestState.DRAFT,
                mutation = InformationRequestMutation.CREATE_DRAFT,
                actor = command.access.principal,
                idempotencyKey = "information_request.draft.create|$CREATE_FROM_TEMPLATE_VERSION_OPERATION|" +
                    "${request.id}|${command.idempotencyKey}",
            ),
        )
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

    private fun requireHeldForOwner(reference: InformationRequestTemplateVersionReference, exchange: Exchange)
    {
        if (reference.ownerScopeKind == InformationRequestTemplateScopeKind.PLATFORM)
        {
            throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.PLATFORM_COPY_REQUIRED,
                "A platform Template is copied into the owner's Templates before a request uses it",
            )
        }
        val held = when (reference.ownerScopeKind)
        {
            InformationRequestTemplateScopeKind.PLATFORM -> false
            InformationRequestTemplateScopeKind.ORGANIZATION ->
                exchange.ownerOrganizationId != null && reference.ownerOrganizationId == exchange.ownerOrganizationId
            InformationRequestTemplateScopeKind.PERSONAL ->
                exchange.ownerOrganizationId == null && reference.ownerUserId == exchange.ownerUserId
        }
        if (!held)
        {
            throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.NOT_FOUND,
                "Information request template version not found: ${reference.templateVersionId}",
            )
        }
    }

    private fun requireDraftCreationAllowed(exchange: Exchange)
    {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(status = exchange.status, deleted = exchange.isDeleted, lockedForUpdate = true),
            null,
            InformationRequestMutation.CREATE_DRAFT,
        )
        if (decision is InformationRequestPolicyDecision.Deny)
        {
            throw InformationRequestLifecycleException(decision.reasonCode, "Information Request draft creation is not allowed")
        }
    }

    private fun authorize(command: CreateInformationRequestFromTemplateVersionCommand)
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

    private fun replay(result: CommandResultReference, access: RequestAccessContext): InformationRequestCreationResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST) {
            "Command receipt does not reference an Information Request"
        }
        val request = requestRepository.findById(result.resourceId)
            ?: throw IllegalArgumentException("Information Request receipt target not found")
        InformationRequestReadAuthorization.requireView(authorizationService, request.id, access)
        return InformationRequestCreationResult(
            request = request,
            requestETag = requireNotNull(result.etag) { "Creation receipt did not record an aggregate ETag" },
            requirementCount = requireNotNull(result.revision) { "Creation receipt did not record Requirements" }.toInt(),
        )
    }

    private companion object
    {
        const val CREATE_FROM_TEMPLATE_VERSION_OPERATION = "create-information-request-from-template-version"
    }
}
