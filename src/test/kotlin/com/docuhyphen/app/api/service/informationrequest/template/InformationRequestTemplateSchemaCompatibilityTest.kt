package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.fields.PublishedSchemaVersionRef
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.INFORMATION_REQUEST_SCHEMA_TARGET
import com.docuhyphen.app.api.service.fields.SchemaVersionResolver
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestTemplateSchemaCompatibilityTest
{
    private val organizationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val schemaVersionId = UUID.randomUUID()

    @Test
    fun `an organization template asks about the organization that holds it`()
    {
        val resolver = allowingResolver()
        val compatibility = InformationRequestTemplateSchemaCompatibility(resolver)

        compatibility.requireUsable(organizationDefinition(), schemaVersionId)

        verify(resolver).requireUsablePublishedVersion(
            eq(schemaVersionId),
            eq(INFORMATION_REQUEST_SCHEMA_TARGET),
            eq(ScopeReference.Organization(organizationId)),
        )
    }

    @Test
    fun `a personal template asks about the person who holds it`()
    {
        val resolver = allowingResolver()
        val compatibility = InformationRequestTemplateSchemaCompatibility(resolver)

        compatibility.requireUsable(personalDefinition(), schemaVersionId)

        verify(resolver).requireUsablePublishedVersion(
            eq(schemaVersionId),
            eq(INFORMATION_REQUEST_SCHEMA_TARGET),
            eq(ScopeReference.Personal(userId)),
        )
    }

    @Test
    fun `a document that names no schema version asks nothing`()
    {
        val resolver = allowingResolver()
        val compatibility = InformationRequestTemplateSchemaCompatibility(resolver)

        compatibility.requireUsable(organizationDefinition(), null)

        verify(resolver, never()).requireUsablePublishedVersion(any(), any(), any())
    }

    @Test
    fun `a schema the owner cannot use is refused as a configuration refusal`()
    {
        val resolver = mock<SchemaVersionResolver>()
        doThrow(FieldValidationException("Schema version belongs to a different owner"))
            .whenever(resolver).requireUsablePublishedVersion(any(), any(), any())
        val compatibility = InformationRequestTemplateSchemaCompatibility(resolver)

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            compatibility.requireUsable(organizationDefinition(), schemaVersionId)
        }.message

        assertTrue(
            refusal.contains("different owner"),
            "The refusal carries the reason the schema cannot be used: $refusal",
        )
    }

    private fun allowingResolver(): SchemaVersionResolver
    {
        val resolver = mock<SchemaVersionResolver>()
        whenever(resolver.requireUsablePublishedVersion(any(), any(), any())).thenReturn(
            PublishedSchemaVersionRef(
                schemaVersionId = schemaVersionId,
                schemaDefinitionId = UUID.randomUUID(),
                versionNumber = 3,
                targetResourceType = INFORMATION_REQUEST_SCHEMA_TARGET,
            ),
        )
        return resolver
    }

    private fun organizationDefinition() = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.ORGANIZATION
        scopeOrgId = organizationId
        namespace = "process"
        templateKey = "collection-pattern"
        displayName = "Collection pattern"
    }

    private fun personalDefinition() = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.PERSONAL
        scopeUserId = userId
        namespace = "process"
        templateKey = "collection-pattern"
        displayName = "Collection pattern"
    }
}

