package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Which Schemas a resource may be governed by, now that a person can own one.
 *
 * The rule is a single question asked of two owners: the Schema's owner must be the resource's own
 * owner, or the platform, which publishes for everybody. Reading it as an organization question
 * instead turned every other ownership into a refusal, so a resource one person held could never be
 * given that person's own Schema no matter who owned it.
 *
 * Both directions are covered. A person's Schema must not reach an organization's resource any more
 * than an organization's Schema reaches a person's, because ownership is not a hierarchy: neither
 * owner is inside the other.
 */
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

    /**
     * A refused assignment leaves nothing behind. The Schema is checked before anything is written,
     * so a refusal is not a partial assignment that later reads would have to explain.
     */
    private fun assertNothingWasAssigned(fixture: SchemaAssignmentFieldsFixture)
    {
        assertEquals(emptyList<Any>(), fixture.savedAssignments)
        assertEquals(emptyList<Any>(), fixture.savedSets)
    }

    /**
     * @param resourceOwner null leaves the resource with the fixture's own organization as holder.
     * @param schemaOwner null leaves the Schema owned by that same organization.
     * @param ownerlessResource records no holder at all, which is what the adapter reports for a
     *   resource whose ownership it cannot resolve.
     */
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
