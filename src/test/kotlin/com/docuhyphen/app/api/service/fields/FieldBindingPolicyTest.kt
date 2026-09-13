package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * What a caller may do with one question of an assigned Schema is decided by the resource that owns
 * it, through one policy that both the projection and the save consult.
 *
 * One policy rather than two matters more than it first appears. A projection that hid a question
 * for one reason while a save refused it for another would eventually disagree, and the shape of
 * that disagreement is either a question a caller can see but not answer with no explanation, or a
 * question a caller can answer without ever having been shown it. Asking the same policy for both
 * makes the two answers the same answer.
 *
 * It is also where a resource whose access narrows over time says so. An Exchange has no such
 * narrowing, so its policy is the shared audience and configuration rules and nothing more.
 */
class FieldBindingPolicyTest
{
    private val author = PrincipalRef.user(UUID.randomUUID())

    /** A policy that closes one question outright, as a resource with its own scope rule would. */
    private class ClosedBinding(private val contractId: () -> UUID) : FieldBindingPolicy
    {
        override fun decide(request: FieldBindingAccess): FieldBindingDecision =
            if (request.binding.fieldContractId == contractId())
                FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE)
            else
                FieldBindingDecision.Allow
    }

    /** A policy that shows one question but refuses every attempt to change it. */
    private class ReadOnlyBinding(private val contractId: () -> UUID) : FieldBindingPolicy
    {
        override fun decide(request: FieldBindingAccess): FieldBindingDecision = when
        {
            request.binding.fieldContractId == contractId() && request.operation == FieldValueOperation.WRITE ->
                FieldBindingDecision.Deny(FieldBindingDenial.READ_ONLY)
            else -> FieldBindingDecision.Allow
        }
    }

    @Test
    fun `a question the policy closes is left out of the projection`()
    {
        lateinit var closed: SchemaAssignmentFieldsFixture
        closed = SchemaAssignmentFieldsFixture(
            principal = author, bindingPolicy = ClosedBinding { closed.secondNoteContractId },
        )

        val projected = requireNotNull(closed.read()).fields.map { it.fieldContractId }

        assertTrue(
            closed.secondNoteContractId !in projected,
            "A closed question is not disclosed by the projection",
        )
        assertEquals(listOf(closed.noteContractId, closed.optionsContractId), projected)
    }

    @Test
    fun `a question the policy closes cannot be addressed by a save`()
    {
        lateinit var closed: SchemaAssignmentFieldsFixture
        closed = SchemaAssignmentFieldsFixture(
            principal = author, bindingPolicy = ClosedBinding { closed.secondNoteContractId },
        )

        assertThrows<FieldValidationException> {
            closed.save(listOf(closed.entry(closed.secondNoteContractId, "Answer to a closed question")))
        }

        assertTrue(
            closed.storedByValueSet.values.all { it.isEmpty() },
            "A refused save stores nothing at all",
        )
    }

    @Test
    fun `a question the policy shows but will not accept is projected and refused on save`()
    {
        lateinit var unwritable: SchemaAssignmentFieldsFixture
        unwritable = SchemaAssignmentFieldsFixture(
            principal = author, bindingPolicy = ReadOnlyBinding { unwritable.secondNoteContractId },
        )

        val projected = requireNotNull(unwritable.read()).fields.map { it.fieldContractId }
        assertTrue(unwritable.secondNoteContractId in projected, "A question a caller may read is shown")

        val failure = assertThrows<FieldValidationException> {
            unwritable.save(listOf(unwritable.entry(unwritable.secondNoteContractId, "Answer anyway")))
        }
        assertTrue(
            failure.message.contains("read-only"),
            "The refusal says the question is not the caller's to answer",
        )
    }

    @Test
    fun `a question the policy allows is saved as it always was`()
    {
        val allowed = SchemaAssignmentFieldsFixture(principal = author)

        allowed.save(listOf(allowed.entry(allowed.noteContractId, "Answer to an open question")))

        assertEquals("Answer to an open question", allowed.rootAnswer(allowed.noteContractId)?.textValue)
    }

    // ── The rules every resource shares ───────────────────────────────────────

    @Test
    fun `the shared rules keep an outside caller to the questions classified for everyone`()
    {
        val policy = AudienceBindingPolicy(external = true)

        assertEquals(
            FieldBindingDecision.Allow,
            policy.decide(access(binding(FieldDataClassification.PUBLIC), FieldValueOperation.READ)),
        )
        assertEquals(
            FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE),
            policy.decide(access(binding(FieldDataClassification.INTERNAL), FieldValueOperation.READ)),
        )
    }

    @Test
    fun `the shared rules let an inside caller see every question`()
    {
        val policy = AudienceBindingPolicy(external = false)

        assertEquals(
            FieldBindingDecision.Allow,
            policy.decide(access(binding(FieldDataClassification.INTERNAL), FieldValueOperation.READ)),
        )
    }

    @Test
    fun `the shared rules keep a configured question out of a caller's hands`()
    {
        val policy = AudienceBindingPolicy(external = false)
        val configured = binding(FieldDataClassification.INTERNAL).apply { isReadOnly = true }

        assertEquals(
            FieldBindingDecision.Allow, policy.decide(access(configured, FieldValueOperation.READ)),
            "A question filled by configuration is still one a caller may read",
        )
        assertEquals(
            FieldBindingDecision.Deny(FieldBindingDenial.READ_ONLY),
            policy.decide(access(configured, FieldValueOperation.WRITE)),
        )
    }

    private fun binding(classification: FieldDataClassification) = SchemaFieldBinding().apply {
        id = UUID.randomUUID()
        schemaVersionId = UUID.randomUUID()
        fieldContractId = UUID.randomUUID()
        visibility = classification
    }

    private fun access(binding: SchemaFieldBinding, operation: FieldValueOperation) = FieldBindingAccess(
        resource = FieldsResourceRef("EXCHANGE", UUID.randomUUID()),
        access = FieldsAccessContext(author, com.docuhyphen.app.api.service.auth.authz.AuthorizationContext()),
        valueSet = FieldValueSetRef.Root,
        binding = binding,
        operation = operation,
    )
}
