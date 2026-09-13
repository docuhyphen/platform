package com.docuhyphen.app.api.service.command

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

interface CommandReceiptStore
{
    fun findForCommand(request: CommandReceiptRequest): CommandReceipt?

    fun insert(receipt: CommandReceipt): CommandReceipt
}

data class CommandActorRef(val kind: String, val id: UUID)
{
    init
    {
        require(kind.isNotBlank()) { "Command actor kind is required" }
    }

    companion object
    {
        fun principal(principal: PrincipalRef) = CommandActorRef(principal.kind.name, principal.id)

        fun accessSession(id: UUID) = CommandActorRef(ACCESS_SESSION, id)

        const val ACCESS_SESSION = "ACCESS_SESSION"
    }
}

data class CommandReceiptRequest(
    val resource: ResourceRef,
    val operation: String,
    val actor: CommandActorRef,
    val idempotencyKey: String,
    val requestFingerprint: String,
)
{
    init
    {
        require(operation.isNotBlank()) { "Command operation is required" }
        require(idempotencyKey.isNotBlank()) { "Command idempotency key is required" }
        require(requestFingerprint.isNotBlank()) { "Command request fingerprint is required" }
    }
}

data class CommandResultReference(
    val resourceType: ResourceType,
    val resourceId: UUID,
    val revision: Long? = null,
    val etag: String? = null,
)

data class CommandMutationResult<T>(
    val response: T,
    val result: CommandResultReference,
)

sealed interface CommandReceiptDecision<out T>
{
    data class Recorded<T>(
        val response: T,
        val receipt: CommandReceipt,
    ) : CommandReceiptDecision<T>

    data class Replayed<T>(
        val receipt: CommandReceipt,
        val result: CommandResultReference,
    ) : CommandReceiptDecision<T>
}

class CommandReceiptConflictException(
    override val message: String = "The idempotency key was already used with a different request",
) : RuntimeException(message)
{
    val reasonCode = "COMMAND_RECEIPT_FINGERPRINT_CONFLICT"
}

object CommandRequestFingerprint
{
    fun sha256Hex(canonicalRequest: String): String
    {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(canonicalRequest.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

@ApplicationScoped
class CommandReceiptService @Inject constructor(
    private val receipts: CommandReceiptStore,
)
{
    @Transactional
    fun <T> runOnce(
        request: CommandReceiptRequest,
        mutation: () -> CommandMutationResult<T>,
    ): CommandReceiptDecision<T>
    {
        val existing = receipts.findForCommand(request)
        if (existing != null)
        {
            if (existing.requestFingerprintSha256 != request.requestFingerprint)
                throw CommandReceiptConflictException()
            return CommandReceiptDecision.Replayed(existing, existing.resultReference())
        }

        val completed = mutation()
        val now = Timestamp.from(Instant.now())
        val receipt = CommandReceipt().apply {
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
