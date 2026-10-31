package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class PersonalScopeSchemaVisibilityTest
{
    private val holder: UUID = UUID.randomUUID()
    private val someoneElse: UUID = UUID.randomUUID()

    @Test
    fun `a person's own Schema may govern the resource they hold`()
    {
        val fixture = fixture(
            resourceOwner = ScopeReference.Personal(holder),
            schemaOwner = ScopeReference.Personal(holder),
        )

        fixture.assign()

        val assignment = fixture.savedAssignments.single()
        assertEquals(FieldScopeKind.PERSONAL, assignment.scopeKind)
        assertEquals(holder, assignment.scopeUserId)
        assertNull(assignment.scopeOrgId)
    }

    @Test
    fun `one person's Schema may not govern another person's resource`()
    {
        val fixture = fixture(
            resourceOwner = ScopeReference.Personal(holder),
            schemaOwner = ScopeReference.Personal(someoneElse),
        )

        assertThrows<FieldValidationException> { fixture.assign() }
        assertNothingWasAssigned(fixture)
    }

    @Test
    fun `an organization's Schema may not govern a resource one person holds`()
    {
        val fixture = fixture(
            resourceOwner = ScopeReference.Personal(holder),
            schemaOwner = null,
        )

        assertThrows<FieldValidationException> { fixture.assign() }
        assertNothingWasAssigned(fixture)
    }

    @Test
    fun `a person's Schema may not govern an organization's resource`()
    {
        val fixture = fixture(
            resourceOwner = null,
            schemaOwner = ScopeReference.Personal(holder),
        )

        assertThrows<FieldValidationException> { fixture.assign() }
        assertNothingWasAssigned(fixture)
    }

    @Test
    fun `a platform Schema governs a resource one person holds`()
    {
        val fixture = fixture(
            resourceOwner = ScopeReference.Personal(holder),
            schemaOwner = ScopeReference.Platform,
        )

        fixture.assign()

        val assignment = fixture.savedAssignments.single()
        assertEquals(FieldScopeKind.PLATFORM, assignment.scopeKind)
        assertNull(assignment.scopeUserId)
        assertNull(assignment.scopeOrgId)
    }

    @Test
    fun `an organization's own Schema still governs that organization's resource`()
    {
        val fixture = fixture(resourceOwner = null, schemaOwner = null)

        fixture.assign()

        val assignment = fixture.savedAssignments.single()
        assertEquals(FieldScopeKind.ORGANIZATION, assignment.scopeKind)
        assertEquals(fixture.organizationId, assignment.scopeOrgId)
        assertNull(assignment.scopeUserId)
    }

    @Test
    fun `a resource governed by nobody may still be given a platform Schema and nothing else`()
    {
        val ownerless = fixture(resourceOwner = null, schemaOwner = ScopeReference.Platform, ownerlessResource = true)

        ownerless.assign()

        assertEquals(FieldScopeKind.PLATFORM, ownerless.savedAssignments.single().scopeKind)

        val organizationSchema = fixture(resourceOwner = null, schemaOwner = null, ownerlessResource = true)

        assertThrows<FieldValidationException> { organizationSchema.assign() }
        assertNothingWasAssigned(organizationSchema)
    }

    @Test
    fun `another owner's retired Schema is refused as another owner's without describing its lifecycle`() {
        val foreign = SchemaAssignmentFieldsFixture(
            assigned = false,
            rootSetExists = false,
            schemaScope = ScopeReference.Organization(UUID.randomUUID()),
            schemaStatus = FieldLifecycleStatus.RETIRED,
        )

        listOf({ foreign.assign() }, { foreign.assignForCreation() }).forEach { attempt ->
            val refusal = assertThrows<FieldValidationException> { attempt() }.message.orEmpty()
            assertTrue(refusal.contains("different owner"), refusal)
            assertFalse(refusal.contains("retired"), refusal)
        }
        assertNothingWasAssigned(foreign)
    }

    private fun assertNothingWasAssigned(fixture: SchemaAssignmentFieldsFixture)
    {
        assertEquals(emptyList<Any>(), fixture.savedAssignments)
        assertEquals(emptyList<Any>(), fixture.savedSets)
    }

    private fun fixture(
        resourceOwner: ScopeReference?,
        schemaOwner: ScopeReference?,
        ownerlessResource: Boolean = false,
    ) = SchemaAssignmentFieldsFixture(
        assigned = false,
        rootSetExists = false,
        ownerScope = resourceOwner,
        schemaScope = schemaOwner,
        ownerlessResource = ownerlessResource,
    )
}
