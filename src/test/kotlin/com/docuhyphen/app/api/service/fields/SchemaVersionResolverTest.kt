package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

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

    @Test
    fun `a schema another owner holds is refused before its lifecycle, target, or authored key is described`() {
        val foreignOrganization = UUID.randomUUID()
        val foreignPerson = UUID.randomUUID()
        val withheld = listOf("retired", "published", ResourceType.EXCHANGE.name, "process", "collected-data")
        listOf(
            organizationSchema(foreignOrganization) to ScopeReference.Organization(organizationId),
            organizationSchema(foreignOrganization) to ScopeReference.Personal(userId),
            organizationSchema(foreignOrganization) to ScopeReference.Platform,
            personalSchema(foreignPerson) to ScopeReference.Personal(userId),
            personalSchema(foreignPerson) to ScopeReference.Organization(organizationId),
        ).forEach { (definition, owner) ->
            listOf(
                fixture(definition = definition.copyWith { status = FieldLifecycleStatus.RETIRED }),
                fixture(definition = definition.copyWith { }, versionStatus = FieldLifecycleStatus.DRAFT),
                fixture(definition = definition.copyWith { targetResourceType = ResourceType.EXCHANGE.name }),
            ).forEach { candidate ->
                val refusal = refusalFor(candidate, owner)
                assertTrue(refusal.contains("owner"), "The refusal says the schema is not this owner's: $refusal")
                withheld.forEach { detail ->
                    assertFalse(refusal.contains(detail), "The refusal withholds $detail from $owner: $refusal")
                }
            }
        }
    }

    @Test
    fun `the owner of a retired schema is still told which schema retired`() {
        val fixture = fixture(
            definition = personalSchema(userId).apply { status = FieldLifecycleStatus.RETIRED },
        )

        val refusal = refusalFor(fixture, ScopeReference.Personal(userId))

        assertTrue(refusal.contains("process:collected-data"), "The owner sees which schema retired: $refusal")
    }

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

    private fun SchemaDefinition.copyWith(adjust: SchemaDefinition.() -> Unit): SchemaDefinition {
        val source = this
        return schema().apply {
            scopeKind = source.scopeKind
            scopeOrgId = source.scopeOrgId
            scopeUserId = source.scopeUserId
            adjust()
        }
    }
}

