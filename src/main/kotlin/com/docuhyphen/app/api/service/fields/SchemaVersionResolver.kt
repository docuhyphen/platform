package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/** One exact frozen typed-data contract, resolved for a stated owner and a stated resource kind. */
data class PublishedSchemaVersionRef(
    val schemaVersionId: UUID,
    val schemaDefinitionId: UUID,
    val versionNumber: Int,
    val targetResourceType: String,
)

/**
 * Resolves one exact Schema Version named by configuration that outlives the moment it was authored.
 *
 * This is deliberately not the assign-latest path. Naming a Version is a decision to resolve typed
 * answers against exactly the contract that was frozen, so nothing here falls back to a newer
 * Version of the same Schema when the named one turns out to be unusable.
 */
@ApplicationScoped
class SchemaVersionResolver @Inject constructor(
    private val schemaVersionRepository: SchemaVersionRepository,
    private val schemaDefinitionRepository: SchemaDefinitionRepository,
)
{
    /**
     * @throws FieldValidationException when the named Version does not exist, has not frozen, is
     * written for another kind of resource, has been retired, or belongs to another owner.
     */
    fun requireUsablePublishedVersion(
        schemaVersionId: UUID,
        targetResourceType: String,
        owner: ScopeReference,
    ): PublishedSchemaVersionRef
    {
        val version = schemaVersionRepository.findById(schemaVersionId)
            ?: throw FieldValidationException("Schema version not found: $schemaVersionId")
        if (version.status != FieldLifecycleStatus.PUBLISHED)
        {
            throw FieldValidationException(
                "Schema version $schemaVersionId is not published, so nothing can be resolved " +
                    "against it",
            )
        }

        val definition = schemaDefinitionRepository.findById(version.schemaDefinitionId)
            ?: throw FieldValidationException(
                "Schema version $schemaVersionId belongs to a schema that no longer exists",
            )
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
        if (!visibleTo(definition, owner))
        {
            throw FieldValidationException(
                "Schema version $schemaVersionId belongs to a different owner and cannot govern " +
                    "this configuration",
            )
        }

        return PublishedSchemaVersionRef(
            schemaVersionId = version.id,
            schemaDefinitionId = definition.id,
            versionNumber = version.versionNumber,
            targetResourceType = definition.targetResourceType,
        )
    }

    /**
     * What the platform publishes is available to everybody; anything else is available to the one
     * owner that published it. There is no wider audience, because a typed-data contract carries
     * the vocabulary of whoever wrote it.
     */
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
            // The platform holds only what it published, which returned above.
            ScopeReference.Platform -> false
        }
    }
}




