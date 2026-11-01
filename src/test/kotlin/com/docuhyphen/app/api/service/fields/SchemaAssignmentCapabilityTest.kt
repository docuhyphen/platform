package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.*

class SchemaAssignmentCapabilityTest {
    private val caller = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `an owner of an editable resource may choose its schema and edit its values`() {
        val state = SchemaAssignmentFieldsFixture(principal = caller).readState()

        assertNotNull(state.assignment)
        assertTrue(state.canManageSchema)
        assertTrue(state.canEditValues)
    }

    @Test
    fun `a collaborator with edit access may edit values but not choose the schema`() {
        val state = SchemaAssignmentFieldsFixture(principal = caller, manageSchemaAllowed = false).readState()

        assertFalse(state.canManageSchema)
        assertTrue(state.canEditValues)
    }

    @Test
    fun `a reader may neither choose the schema nor edit its values`() {
        val state = SchemaAssignmentFieldsFixture(principal = caller, manageFieldsAllowed = false).readState()

        assertNotNull(state.assignment, "A reader still reads the assignment")
        assertFalse(state.canManageSchema)
        assertFalse(state.canEditValues)
    }

    @Test
    fun `nobody may change the schema or values of a resource past its editable stage`() {
        val state = SchemaAssignmentFieldsFixture(principal = caller, editable = false).readState()

        assertFalse(state.canManageSchema)
        assertFalse(state.canEditValues)
    }

    @Test
    fun `a resource with no schema still says whether the caller may choose one`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = caller, assigned = false, rootSetExists = false)

        val state = fixture.readState()

        assertNull(state.assignment)
        assertTrue(state.canManageSchema)
        assertTrue(state.canEditValues)
    }

    @Test
    fun `reading what a caller may do never consults the subscription`() {
        val fixture = SchemaAssignmentFieldsFixture(principal = caller)

        fixture.readState()

        verify(fixture.subscriptionGuard, never()).requireResourceMutation(anyOrNull())
        assertEquals(emptyList<Any>(), fixture.auditDrafts, "A read records nothing")
    }
}
