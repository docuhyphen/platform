package com.docuhyphen.app.api.service.command

import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class CommandReceiptServiceTest
{
    private val repository = InMemoryCommandReceiptStore()
    private val scope = CommandReceiptScope()
    private val service = CommandReceiptService(repository, scope)
    private val resource = ResourceRef.informationRequest(UUID.randomUUID())
    private val actor = CommandActorRef.principal(PrincipalRef.user(UUID.randomUUID()))

    @Test
    fun `a receipt key fits its storage boundary before a mutation is attempted`() {
        val accepted = CommandReceiptRequest(resource, "record", actor, "k".repeat(256), "f".repeat(64))
        assertEquals(256, accepted.idempotencyKey.length)
        val refused = assertThrows<IllegalArgumentException> {
            CommandReceiptRequest(resource, "record", actor, "k".repeat(257), "f".repeat(64))
        }
        assertTrue(refused.message.orEmpty().contains("256"))
    }

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
    fun `a command holds its own key before it looks up or records its receipt, and a replay before it reads one`() {
        val command = command(idempotencyKey = "locked-first")

        service.runOnce(command) { CommandMutationResult("created", result()) }
        service.runOnce(command) { CommandMutationResult("created-again", result()) }

        assertEquals(
            listOf(
                "lock:locked-first",
                "find:locked-first",
                "insert:locked-first",
                "lock:locked-first",
                "find:locked-first"
            ),
            repository.calls,
        )
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

    @Test
    fun `the mutation runs under the receipt it is stored with and its idempotency key`() {
        var seen: CommandReceiptReference? = null

        val decision = service.runOnce(command(idempotencyKey = "save-answer")) {
            seen = scope.current()
            CommandMutationResult("saved", result())
        }

        val stored = repository.receipts.single()
        assertEquals(CommandReceiptReference(stored.id, "save-answer"), seen)
        assertEquals(stored.id, (decision as CommandReceiptDecision.Recorded<String>).receipt.id)
        assertNull(scope.current())
    }

    @Test
    fun `a failed mutation stores no receipt and leaves no receipt in scope`() {
        var seen: CommandReceiptReference? = null

        assertThrows<IllegalStateException> {
            service.runOnce<String>(command(idempotencyKey = "refused-save")) {
                seen = scope.current()
                throw IllegalStateException("refused")
            }
        }

        assertEquals("refused-save", seen?.idempotencyKey)
        assertTrue(repository.receipts.isEmpty())
        assertNull(scope.current())
    }

    @Test
    fun `a nested command runs under its own receipt and then restores the enclosing one`() {
        val seen = mutableListOf<CommandReceiptReference?>()

        service.runOnce(command(idempotencyKey = "outer")) {
            seen += scope.current()
            service.runOnce(command(operation = "nested", idempotencyKey = "inner")) {
                seen += scope.current()
                CommandMutationResult("inner", result())
            }
            seen += scope.current()
            CommandMutationResult("outer", result())
        }

        val outer = CommandReceiptReference(repository.receipts.single { it.idempotencyKey == "outer" }.id, "outer")
        val inner = CommandReceiptReference(repository.receipts.single { it.idempotencyKey == "inner" }.id, "inner")
        assertEquals(listOf(outer, inner, outer), seen)
        assertNull(scope.current())
    }

    @Test
    fun `a replay runs no mutation and leaves no receipt in scope`() {
        val command = command(idempotencyKey = "replayed")
        service.runOnce(command) { CommandMutationResult("first", result()) }
        var replayRan = false

        val replay = service.runOnce(command) {
            replayRan = true
            CommandMutationResult("again", result())
        }

        assertTrue(replay is CommandReceiptDecision.Replayed)
        assertFalse(replayRan)
        assertNull(scope.current())
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
    val calls = mutableListOf<String>()

    override fun lockCommand(request: CommandReceiptRequest) {
        calls += "lock:${request.idempotencyKey}"
    }

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.also { calls += "find:${request.idempotencyKey}" }.firstOrNull {
            it.resourceType == request.resource.type &&
                    it.resourceId == request.resource.id &&
                    it.operationName == request.operation &&
                    it.actorKind == request.actor.kind &&
                    it.actorId == request.actor.id &&
                    it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        calls += "insert:${receipt.idempotencyKey}"
        receipts += receipt
        return receipt
    }
}
