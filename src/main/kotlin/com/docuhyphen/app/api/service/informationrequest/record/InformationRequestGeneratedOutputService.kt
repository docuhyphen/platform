package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestGeneratedOutput
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.externalsource.RecordInformationRequestGeneratedOutputCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestGeneratedOutputRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestConnectorService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.util.UUID

@ApplicationScoped
class InformationRequestGeneratedOutputService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val queryService: InformationRequestQueryService,
    private val outputRepository: InformationRequestGeneratedOutputRepository,
    private val packageRepository: InformationRequestSubmissionPackageRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional
    fun record(command: RecordInformationRequestGeneratedOutputCommand): InformationRequestGeneratedOutput
    {
        val fingerprint = listOf(
            OPERATION, command.requestId, command.packageId ?: "", command.outputKey, command.externalReference,
            command.contentHashSha256 ?: "", command.mediaType ?: "", command.producedBySource, command.producedAt,
        ).joinToString("|")
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(fingerprint),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val output = recordOnce(command)
                CommandMutationResult(output, CommandResultReference(ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE, output.id, 1, null))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES), command.requestId)
                requireNotNull(outputRepository.findById(decision.result.resourceId))
            }
        }
    }

    fun outputs(requestId: UUID, access: RequestAccessContext): List<InformationRequestGeneratedOutput>
    {
        queryService.findById(requestId, access)
        InformationRequestConnectorService.requireRequestingSide(gate, access, requestId)
        return outputRepository.findForRequest(requestId)
    }

    private fun recordOnce(command: RecordInformationRequestGeneratedOutputCommand): InformationRequestGeneratedOutput
    {
        val locked = gate.lock(command.requestId)
        gate.requireMutation(locked, InformationRequestMutation.RECORD_GENERATED_OUTPUT)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES), command.requestId)
        val outputKey = command.outputKey.trim()
        val reference = command.externalReference.trim()
        val producedBy = command.producedBySource.trim()
        val mediaType = command.mediaType?.trim()?.ifEmpty { null }
        val hash = command.contentHashSha256?.trim()?.lowercase()?.ifEmpty { null }
        if (!MACHINE_KEY.matches(outputKey)) throw InformationRequestCommandRequestException("A generated output is named by a lowercase key")
        if (reference.isEmpty() || reference.length > REFERENCE_LENGTH)
        {
            throw InformationRequestCommandRequestException("A generated output states where it lives in at most $REFERENCE_LENGTH characters")
        }
        if (producedBy.isEmpty() || producedBy.length > SOURCE_LENGTH)
        {
            throw InformationRequestCommandRequestException("A generated output states what produced it in at most $SOURCE_LENGTH characters")
        }
        if (mediaType != null && mediaType.length > MEDIA_TYPE_LENGTH) throw InformationRequestCommandRequestException("A media type is at most $MEDIA_TYPE_LENGTH characters")
        if (hash != null && !SHA256.matches(hash)) throw InformationRequestCommandRequestException("A content hash is a SHA-256 hex digest")
        if (command.producedAt.isAfter(clock.instant().plus(CLOCK_TOLERANCE)))
        {
            throw InformationRequestCommandRequestException("A generated output cannot have been produced in the future")
        }
        if (command.packageId != null && packageRepository.findById(command.packageId)?.informationRequestId != command.requestId)
        {
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Submission package not found")
        }
        val output = outputRepository.save(
            InformationRequestGeneratedOutput().apply {
                informationRequestId = command.requestId
                packageId = command.packageId
                this.outputKey = outputKey
                externalReference = reference
                contentHashSha256 = hash
                this.mediaType = mediaType
                producedBySource = producedBy
                producedAt = Timestamp.from(command.producedAt)
                recordedByPrincipalKind = command.access.principal.kind
                recordedByPrincipalId = command.access.principal.id
                recordedAt = Timestamp.from(clock.instant())
            },
        )
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = locked.request,
                fromState = locked.request.state,
                toState = locked.request.state,
                mutation = InformationRequestMutation.RECORD_GENERATED_OUTPUT,
                actor = command.access.principal,
                idempotencyKey = "information_request.external|output|${output.id}",
                details = mapOf("generatedOutputId" to output.id.toString(), "outputKey" to outputKey),
            ),
        )
        return output
    }

    private companion object
    {
        const val OPERATION = "record-information-request-generated-output"
        const val REFERENCE_LENGTH = 512
        const val SOURCE_LENGTH = 256
        const val MEDIA_TYPE_LENGTH = 128
        val CLOCK_TOLERANCE: Duration = Duration.ofMinutes(5)
        val MACHINE_KEY = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}
