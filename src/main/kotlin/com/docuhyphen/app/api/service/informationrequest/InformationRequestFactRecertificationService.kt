package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFact
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestFactRecertification
import com.docuhyphen.app.api.model.entity.InformationRequestFactRecertificationEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAcceptedFactOffer
import com.docuhyphen.app.api.model.informationrequest.InformationRequestFactRecertificationResult
import com.docuhyphen.app.api.model.informationrequest.InformationRequestFactRecertificationView
import com.docuhyphen.app.api.model.informationrequest.RecertifyInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestFactRecertificationEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestFactRecertificationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldValueReadCommand
import com.docuhyphen.app.api.service.fields.FieldValueSetRef
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.json.Json
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestFactRecertificationService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val factRepository: InformationRequestAcceptedFactRepository,
    private val factQueries: InformationRequestAcceptedFactQueryService,
    private val responses: InformationRequestResponseDraftService,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val schemaAssignmentRepository: SchemaAssignmentRepository,
    private val schemaFieldBindingRepository: SchemaFieldBindingRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val recertificationRepository: InformationRequestFactRecertificationRepository,
    private val recertificationEvidenceRepository: InformationRequestFactRecertificationEvidenceRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional
    fun recertify(command: RecertifyInformationRequestAcceptedFactCommand): InformationRequestFactRecertificationResult
    {
        if (!command.assented)
        {
            throw InformationRequestCommandRequestException("Reusing an earlier answer requires the respondent's explicit confirmation")
        }
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                "$OPERATION|${command.requestId}|${command.factId}|${command.requirementId}",
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = recertifyOnce(command)
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        ResourceType.INFORMATION_REQUEST_FACT_RECERTIFICATION,
                        result.view.recertification.id,
                        result.view.recertification.responseRevision,
                        result.responseETag,
                    ),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(command, decision.result)
        }
    }

    private fun recertifyOnce(command: RecertifyInformationRequestAcceptedFactCommand): InformationRequestFactRecertificationResult
    {
        val locked = gate.lock(command.requestId)
        gate.requireMutation(locked, InformationRequestMutation.RECERTIFY_FACT)
        factRepository.findByIdForUpdate(command.factId) ?: unavailable()
        val offer = currentOffer(command)
        val fact = offer.fact.fact
        val requirement = requirementRepository.findForRequest(command.requestId).firstOrNull { it.id == command.requirementId }
            ?: unavailable()
        val draft = responses.patch(
            PatchInformationRequestResponsesCommand(
                requestId = command.requestId,
                access = command.access,
                precondition = command.precondition,
                idempotencyKey = "fact-recertification|${command.idempotencyKey}",
                patches = listOf(
                    InformationRequestResponsePatch(
                        requirementId = requirement.id,
                        disposition = InformationRequestResponseDisposition.PROVIDED,
                        fieldValues = ResponseFieldValuesPatch(
                            entries = listOf(fieldEntry(locked.request, fact)),
                            precondition = fieldPrecondition(locked.request, requirement, command),
                        ),
                    ),
                ),
            ),
        )
        val response = draft.responses.firstOrNull { it.informationRequestRequirementId == requirement.id } ?: unavailable()
        val recertification = recertificationRepository.save(
            InformationRequestFactRecertification().apply {
                informationRequestId = command.requestId
                informationRequestRequirementId = requirement.id
                responseId = response.id
                responseRevision = response.responseRevision
                factId = fact.id
                purposeKey = fact.purposeKey
                policyBasisKey = fact.policyBasisKey
                valueType = fact.valueType
                canonicalValue = fact.canonicalValue
                sourceInformationRequestId = fact.sourceInformationRequestId
                sourcePackageId = fact.sourcePackageId
                sourceSubmissionItemId = fact.sourceSubmissionItemId
                sourceRequirementId = fact.sourceRequirementId
                sourceFieldValueRevisionId = fact.sourceFieldValueRevisionId
                sourceReviewId = fact.sourceReviewId
                assentedByPrincipalKind = command.access.principal.kind
                assentedByPrincipalId = command.access.principal.id
                assentedBySessionRef = command.access.authorization.sessionRef
                assentedAt = Timestamp.from(clock.instant())
            },
        )
        offer.fact.evidenceVersionIds.forEach { evidenceVersionId ->
            recertificationEvidenceRepository.save(
                InformationRequestFactRecertificationEvidence().apply {
                    recertificationId = recertification.id
                    this.evidenceVersionId = evidenceVersionId
                },
            )
        }
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = draft.request,
                fromState = draft.request.state,
                toState = draft.request.state,
                mutation = InformationRequestMutation.RECERTIFY_FACT,
                actor = command.access.principal,
                idempotencyKey = "information_request.fact_recertification|${recertification.id}|${command.idempotencyKey}",
                details = mapOf(
                    "acceptedFactId" to fact.id.toString(),
                    "requirementId" to requirement.id.toString(),
                    "responseRevision" to response.responseRevision.toString(),
                ),
            ),
        )
        return InformationRequestFactRecertificationResult(
            InformationRequestFactRecertificationView(recertification, offer.fact.evidenceVersionIds),
            draft.responseETag,
        )
    }

    private fun currentOffer(command: RecertifyInformationRequestAcceptedFactCommand): InformationRequestAcceptedFactOffer =
        factQueries.offers(command.requestId, command.access)
            .firstOrNull { it.requirementId == command.requirementId && it.fact.fact.id == command.factId }
            ?.takeIf { it.fact.fact.visibility == InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES }
            ?: unavailable()

    private fun fieldEntry(request: InformationRequest, fact: InformationRequestAcceptedFact): FieldValueEntry
    {
        val assignment = schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, request.id) ?: unavailable()
        val binding = schemaFieldBindingRepository.findByVersion(assignment.schemaVersionId)
            .firstOrNull { it.fieldDefinitionId == fact.fieldDefinitionId }
            ?: unavailable()
        if (fieldContractRepository.findById(binding.fieldContractId)?.valueType != fact.valueType) unavailable()
        return FieldValueEntry(binding.fieldContractId, Json.parseToJsonElement(fact.canonicalValue))
    }

    private fun fieldPrecondition(
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        command: RecertifyInformationRequestAcceptedFactCommand,
    ): FieldsPrecondition
    {
        val current = schemaAssignmentService.getAssignment(
            FieldValueReadCommand(
                resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id),
                access = FieldsAccessContext(command.access.principal, command.access.authorization),
                valueSet = if (requirement.occurrencePath == ROOT_OCCURRENCE_PATH) FieldValueSetRef.Root
                else FieldValueSetRef.Occurrence(requirement.occurrencePath),
            ),
        )?.etag
        return if (current == null) FieldsPrecondition.Unconditioned else FieldsPrecondition.ExpectedRevision(current)
    }

    private fun replay(
        command: RecertifyInformationRequestAcceptedFactCommand,
        result: CommandResultReference,
    ): InformationRequestFactRecertificationResult
    {
        gate.authorizeRequirement(command.access, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, command.requirementId)
        val recertification = recertificationRepository.findById(result.resourceId)
            ?.takeIf { it.informationRequestId == command.requestId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Recertification not found")
        val evidence = recertificationEvidenceRepository.findForRecertifications(listOf(recertification.id)).map { it.evidenceVersionId }
        return InformationRequestFactRecertificationResult(
            InformationRequestFactRecertificationView(recertification, evidence),
            requireNotNull(result.etag) { "A recertification receipt records the response ETag it produced" },
        )
    }

    private fun unavailable(): Nothing =
        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.ACCEPTED_FACT_OFFER_UNAVAILABLE,
            "This earlier answer is no longer available to reuse here",
        )

    private companion object
    {
        const val OPERATION = "recertify-information-request-accepted-fact"
        const val ROOT_OCCURRENCE_PATH = "root"
    }
}
