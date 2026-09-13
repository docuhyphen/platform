package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Naming one exact Schema Version is a different question from choosing whichever version of a
 * Schema is newest, and it is asked by configuration that outlives the moment it was authored. A
 * named Version therefore has to be one that exists, that has frozen, that is written for the kind
 * of resource it will govern, and that its namer is allowed to see at all.
 *
 * The owner check is the one that matters most: a typed-data contract belongs to whoever published
 * it, so naming another tenant's Version would let configuration reach a vocabulary its own owner
 * was never shown.
 */
class SchemaVersionResolverTest
{
    private val organizationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `an organization may name a version of its own schema`()
    {
        val fixture = fixture(definition = organizationSchema(organizationId))

        val resolved = fixture.resolver.requireUsablePublishedVersion(
            fixture.versionId,
            INFORMATION_REQUEST_SCHEMA_TARGET,
            ScopeReference.Organization(organizationId),
        )

        assertEquals(fixture.versionId, resolved.schemaVersionId)
        assertEquals(INFORMATION_REQUEST_SCHEMA_TARGET, resolved.targetResourceType)
    }

    @Test
    fun `anybody may name a version of a schema the platform published`()
    {
        val fixture = fixture(definition = platformSchema())

        val forOrganization = fixture.resolver.requireUsablePublishedVersion(
            fixture.versionId,
            INFORMATION_REQUEST_SCHEMA_TARGET,
            ScopeReference.Organization(organizationId),
        )
        val forPerson = fixture.resolver.requireUsablePublishedVersion(
            fixture.versionId,
            INFORMATION_REQUEST_SCHEMA_TARGET,
            ScopeReference.Personal(userId),
        )

        assertEquals(fixture.versionId, forOrganization.schemaVersionId)
        assertEquals(fixture.versionId, forPerson.schemaVersionId)
    }

    @Test
    fun `a person may name a version of their own schema and not of somebody else's`()
    {
        val fixture = fixture(definition = personalSchema(userId))

        assertEquals(
            fixture.versionId,
            fixture.resolver.requireUsablePublishedVersion(
                fixture.versionId,
                INFORMATION_REQUEST_SCHEMA_TARGET,
                ScopeReference.Personal(userId),
            ).schemaVersionId,
        )

        val refusal = refusalFor(fixture, ScopeReference.Personal(UUID.randomUUID()))
        assertTrue(refusal.contains("owner"), "The refusal says whose schema it is: $refusal")
    }

    @Test
    fun `one organization cannot name a version of another organization's schema`()
    {
        val fixture = fixture(definition = organizationSchema(UUID.randomUUID()))

        val refusal = refusalFor(fixture, ScopeReference.Organization(organizationId))

        assertTrue(refusal.contains("owner"), "The refusal says whose schema it is: $refusal")
    }

    @Test
    fun `a version that does not exist is refused rather than resolved to nothing`()
    {
        val resolver = SchemaVersionResolver(
            schemaVersionRepository = mock(),
            schemaDefinitionRepository = mock(),
        )
        val absent = UUID.randomUUID()

        val refusal = assertThrows<FieldValidationException> {
            resolver.requireUsablePublishedVersion(
                absent,
                INFORMATION_REQUEST_SCHEMA_TARGET,
                ScopeReference.Organization(organizationId),
            )
        }.message

        assertTrue(refusal.contains(absent.toString()), "The refusal names the version asked for: $refusal")
    }

    @Test
    fun `a version that has not frozen cannot be named, because it can still change`()
    {
        val fixture = fixture(
            definition = organizationSchema(organizationId),
            versionStatus = FieldLifecycleStatus.DRAFT,
        )

        val refusal = refusalFor(fixture, ScopeReference.Organization(organizationId))

        assertTrue(refusal.contains("published"), "The refusal says the version has not frozen: $refusal")
    }

    @Test
    fun `a retired schema cannot govern configuration authored after its retirement`()
    {
        val fixture = fixture(
            definition = organizationSchema(organizationId).apply { status = FieldLifecycleStatus.RETIRED },
        )

        val refusal = refusalFor(fixture, ScopeReference.Organization(organizationId))

        assertTrue(refusal.contains("retired"), "The refusal says the schema is retired: $refusal")
    }

    @Test
    fun `a schema written for one kind of resource cannot govern another`()
    {
        val fixture = fixture(
            definition = organizationSchema(organizationId).apply {
                targetResourceType = ResourceType.EXCHANGE.name
            },
        )

        val refusal = refusalFor(fixture, ScopeReference.Organization(organizationId))

        assertTrue(
            refusal.contains(ResourceType.EXCHANGE.name) &&
                refusal.contains(INFORMATION_REQUEST_SCHEMA_TARGET),
            "The refusal names what the schema is written for and what it was asked to govern: $refusal",
        )
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private class Fixture(val resolver: SchemaVersionResolver, val versionId: UUID)

    private fun fixture(
        definition: SchemaDefinition,
        versionStatus: FieldLifecycleStatus = FieldLifecycleStatus.PUBLISHED,
    ): Fixture
    {
        val version = SchemaVersion().apply {
            schemaDefinitionId = definition.id
            versionNumber = 3
            status = versionStatus
        }
        val versionRepository = mock<SchemaVersionRepository>()
        whenever(versionRepository.findById(version.id)).thenReturn(version)
        val definitionRepository = mock<SchemaDefinitionRepository>()
        whenever(definitionRepository.findById(definition.id)).thenReturn(definition)

        return Fixture(
            SchemaVersionResolver(
                schemaVersionRepository = versionRepository,
                schemaDefinitionRepository = definitionRepository,
            ),
            version.id,
        )
    }

    private fun refusalFor(fixture: Fixture, owner: ScopeReference): String =
        assertThrows<FieldValidationException> {
            fixture.resolver.requireUsablePublishedVersion(
                fixture.versionId,
                INFORMATION_REQUEST_SCHEMA_TARGET,
                owner,
            )
        }.message

    private fun schema() = SchemaDefinition().apply {
        namespace = "process"
        schemaKey = "collected-data"
        displayName = "Collected data"
        targetResourceType = INFORMATION_REQUEST_SCHEMA_TARGET
        status = FieldLifecycleStatus.PUBLISHED
    }

    private fun organizationSchema(owner: UUID) = schema().apply {
        scopeKind = FieldScopeKind.ORGANIZATION
        scopeOrgId = owner
    }

    private fun personalSchema(owner: UUID) = schema().apply {
        scopeKind = FieldScopeKind.PERSONAL
        scopeUserId = owner
    }

    private fun platformSchema() = schema().apply { scopeKind = FieldScopeKind.PLATFORM }
}

