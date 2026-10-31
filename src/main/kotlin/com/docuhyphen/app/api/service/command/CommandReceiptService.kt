package com.docuhyphen.app.api.service.command

import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.CommandReceipt
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

interface CommandReceiptStore
{
    fun lockCommand(request: CommandReceiptRequest)

    fun findForCommand(request: CommandReceiptRequest): CommandReceipt?

    fun insert(receipt: CommandReceipt): CommandReceipt
}

class CommandReceiptConflictException(
    override val message: String = "The idempotency key was already used with a different request",
) : RuntimeException(message)
{
    val reasonCode = "COMMAND_RECEIPT_FINGERPRINT_CONFLICT"
}

@ApplicationScoped
class CommandReceiptService @Inject constructor(
    private val receipts: CommandReceiptStore,
    private val scope: CommandReceiptScope,
)
{
    fun isRecorded(request: CommandReceiptRequest): Boolean = receipts.findForCommand(request) != null

    @Transactional
    fun <T> runOnce(
        request: CommandReceiptRequest,
        mutation: () -> CommandMutationResult<T>,
    ): CommandReceiptDecision<T>
    {
        receipts.lockCommand(request)
        val existing = receipts.findForCommand(request)
        if (existing != null)
        {
            if (existing.requestFingerprintSha256 != request.requestFingerprint)
                throw CommandReceiptConflictException()
            return CommandReceiptDecision.Replayed(existing, existing.resultReference())
        }

        val receiptId = UUID.randomUUID()
        val completed = scope.within(CommandReceiptReference(receiptId, request.idempotencyKey)) { mutation() }
        val now = Timestamp.from(Instant.now())
        val receipt = CommandReceipt().apply {
            id = receiptId
            resourceType = request.resource.type
            resourceId = request.resource.id
            operationName = request.operation
            actorKind = request.actor.kind
            actorId = request.actor.id
            idempotencyKey = request.idempotencyKey
            requestFingerprintSha256 = request.requestFingerprint
            resultResourceType = completed.result.resourceType
            resultResourceId = completed.result.resourceId
            resultRevision = completed.result.revision
            resultETag = completed.result.etag
            createdAt = now
            completedAt = now
        }
        return CommandReceiptDecision.Recorded(completed.response, receipts.insert(receipt))
    }

    private fun CommandReceipt.resultReference() = CommandResultReference(
        resourceType = resultResourceType,
        resourceId = resultResourceId,
        revision = resultRevision,
        etag = resultETag,
    )
}
