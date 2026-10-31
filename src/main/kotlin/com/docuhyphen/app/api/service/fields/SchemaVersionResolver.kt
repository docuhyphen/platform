package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.fields.PublishedSchemaVersionRef
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class SchemaVersionResolver @Inject constructor(
    private val schemaVersionRepository: SchemaVersionRepository,
    private val schemaDefinitionRepository: SchemaDefinitionRepository,
)
{
    fun requireUsablePublishedVersion(
        schemaVersionId: UUID,
        targetResourceType: String,
        owner: ScopeReference,
    ): PublishedSchemaVersionRef
    {
        val version = schemaVersionRepository.findById(schemaVersionId)
            ?: throw FieldValidationException("Schema version not found: $schemaVersionId")
        val definition = schemaDefinitionRepository.findById(version.schemaDefinitionId)
            ?: throw FieldValidationException(
                "Schema version $schemaVersionId belongs to a schema that no longer exists",
            )
        if (!visibleTo(definition, owner)) {
            throw FieldValidationException(
                "Schema version $schemaVersionId belongs to a different owner and cannot govern " +
                        "this configuration",
            )
        }
        if (version.status != FieldLifecycleStatus.PUBLISHED)
        {
            throw FieldValidationException(
                "Schema version $schemaVersionId is not published, so nothing can be resolved " +
                        "against it",
            )
        }
        if (definition.status == FieldLifecycleStatus.RETIRED)
        {
            throw FieldValidationException(
                "Schema ${definition.namespace}:${definition.schemaKey} is retired and cannot " +
                        "govern configuration authored now",
            )
        }
        if (definition.targetResourceType != targetResourceType)
        {
            throw FieldValidationException(
                "Schema version $schemaVersionId is written for ${definition.targetResourceType} " +
                        "rather than $targetResourceType",
            )
        }

        return PublishedSchemaVersionRef(
            schemaVersionId = version.id,
            schemaDefinitionId = definition.id,
            versionNumber = version.versionNumber,
            targetResourceType = definition.targetResourceType,
        )
    }

    private fun visibleTo(definition: SchemaDefinition, owner: ScopeReference): Boolean
    {
        if (definition.scopeKind == FieldScopeKind.PLATFORM) return true

        return when (owner)
        {
            is ScopeReference.Organization ->
                definition.scopeKind == FieldScopeKind.ORGANIZATION &&
                        definition.scopeOrgId == owner.organizationId

            is ScopeReference.Personal ->
                definition.scopeKind == FieldScopeKind.PERSONAL &&
                        definition.scopeUserId == owner.userId
            ScopeReference.Platform -> false
        }
    }
}




