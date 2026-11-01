package com.docuhyphen.app.api.service.fields

import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class SchemaAssignmentServiceMutationEntitlementTest
{
    @Test
    fun `setValues checks the live subscription guard when the adapter reports no frozen entitlement`()
    {
        val fixture = SchemaAssignmentFieldsFixture(mutationEntitlementFrozen = false)

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "hello")))

        verify(fixture.subscriptionGuard).requireResourceMutation(anyOrNull())
    }

    @Test
    fun `setValues skips the live subscription guard when the adapter reports a frozen mutation entitlement`()
    {
        val fixture = SchemaAssignmentFieldsFixture(mutationEntitlementFrozen = true)

        fixture.save(listOf(fixture.entry(fixture.noteContractId, "hello")))

        verify(fixture.subscriptionGuard, never()).requireResourceMutation(anyOrNull())
    }
}
