package com.docuhyphen.app.api.model.command

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import java.util.*

data class CommandActorRef(val kind: String, val id: UUID) {
    init {
        require(kind.isNotBlank()) { "Command actor kind is required" }
    }

    companion object {
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
) {
    init {
        require(operation.isNotBlank()) { "Command operation is required" }
        require(idempotencyKey.isNotBlank()) { "Command idempotency key is required" }
        require(idempotencyKey.length <= 256) { "Command idempotency key has at most 256 characters" }
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

sealed interface CommandReceiptDecision<out T> {
    data class Recorded<T>(
        val response: T,
        val receipt: CommandReceipt,
    ) : CommandReceiptDecision<T>

    data class Replayed<T>(
        val receipt: CommandReceipt,
        val result: CommandResultReference,
    ) : CommandReceiptDecision<T>
}
