package com.docuhyphen.app.api.service.command

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class CommandReceiptServiceTest
{
    private val repository = InMemoryCommandReceiptStore()
    private val service = CommandReceiptService(repository)
    private val resource = ResourceRef.informationRequest(UUID.randomUUID())
    private val actor = CommandActorRef.principal(PrincipalRef.user(UUID.randomUUID()))

    @Test
    fun `same key and same fingerprint replays the stored result without running the mutation again`()
    {
        var mutationCount = 0
        val command = command(fingerprint = CommandRequestFingerprint.sha256Hex("""{"answer":"same"}"""))
        val result = CommandResultReference(ResourceType.INFORMATION_REQUEST, UUID.randomUUID(), 7, "\"v7\"")

        val first = service.runOnce(command) {
            mutationCount += 1
            CommandMutationResult("created", result)
        }
        val replay = service.runOnce(command) {
            mutationCount += 1
            CommandMutationResult("created-again", result.copy(revision = 8))
        }

        assertTrue(first is CommandReceiptDecision.Recorded)
        assertTrue(replay is CommandReceiptDecision.Replayed)
        assertEquals(1, mutationCount)
        assertEquals(result, (replay as CommandReceiptDecision.Replayed<String>).result)
        assertEquals("created", (first as CommandReceiptDecision.Recorded<String>).response)
    }

    @Test
    fun `same key with a different fingerprint is refused before the mutation runs`()
    {
        val idempotencyKey = "issue-request"
        service.runOnce(command(idempotencyKey = idempotencyKey, fingerprint = "first")) {
            CommandMutationResult("created", result())
        }

        var mutationRan = false
        val refusal = assertThrows<CommandReceiptConflictException> {
            service.runOnce(command(idempotencyKey = idempotencyKey, fingerprint = "second")) {
                mutationRan = true
                CommandMutationResult("created-again", result())
            }
        }

        assertFalse(mutationRan)
        assertEquals("COMMAND_RECEIPT_FINGERPRINT_CONFLICT", refusal.reasonCode)
    }

    @Test
    fun `the same raw idempotency key is independent across resource operation and actor scope`()
    {
        val rawKey = "cancel"
        val baseline = command(idempotencyKey = rawKey)
        val otherResource = baseline.copy(resource = ResourceRef.informationRequest(UUID.randomUUID()))
        val otherOperation = baseline.copy(operation = "supersede")
        val otherActor = baseline.copy(actor = CommandActorRef.principal(PrincipalRef.participant(UUID.randomUUID())))

        service.runOnce(baseline) { CommandMutationResult("baseline", result()) }
        service.runOnce(otherResource) { CommandMutationResult("other-resource", result()) }
        service.runOnce(otherOperation) { CommandMutationResult("other-operation", result()) }
        service.runOnce(otherActor) { CommandMutationResult("other-actor", result()) }

        assertEquals(4, repository.receipts.size)
        assertEquals(4, repository.receipts.map { it.resultResourceId }.toSet().size)
    }

    @Test
    fun `an access session is an explicit actor scope and is not represented by a raw token`()
    {
        val sessionId = UUID.randomUUID()
        val command = command(actor = CommandActorRef.accessSession(sessionId))

        service.runOnce(command) { CommandMutationResult("saved", result()) }

        val stored = repository.receipts.single()
        assertEquals("ACCESS_SESSION", stored.actorKind)
        assertEquals(sessionId, stored.actorId)
        assertNotEquals("raw-token", stored.actorKind)
    }

    private fun command(
        resource: ResourceRef = this.resource,
        actor: CommandActorRef = this.actor,
        operation: String = "issue",
        idempotencyKey: String = "request-key",
        fingerprint: String = CommandRequestFingerprint.sha256Hex("""{"stable":true}"""),
    ) = CommandReceiptRequest(
        resource = resource,
        operation = operation,
        actor = actor,
        idempotencyKey = idempotencyKey,
        requestFingerprint = fingerprint,
    )

    private fun result() = CommandResultReference(ResourceType.INFORMATION_REQUEST, UUID.randomUUID(), 1, "\"v1\"")
}

private class InMemoryCommandReceiptStore : CommandReceiptStore
{
    val receipts = mutableListOf<CommandReceipt>()

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                it.resourceId == request.resource.id &&
                it.operationName == request.operation &&
                it.actorKind == request.actor.kind &&
                it.actorId == request.actor.id &&
                it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}
