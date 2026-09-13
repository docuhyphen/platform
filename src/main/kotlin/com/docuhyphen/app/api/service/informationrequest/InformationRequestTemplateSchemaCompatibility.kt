package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.INFORMATION_REQUEST_SCHEMA_TARGET
import com.docuhyphen.app.api.service.fields.SchemaVersionResolver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Checks that the typed-data contract a configuration names is one its own owner may resolve
 * against.
 *
 * The question is asked while a draft is being authored rather than only when it freezes, because
 * an author can still choose a different Schema Version at that point. It is asked about the owner
 * stored on the Template rather than about the caller, so a copy made for another owner cannot
 * carry a contract that owner was never shown.
 */
@ApplicationScoped
class InformationRequestTemplateSchemaCompatibility @Inject constructor(
    private val schemaVersionResolver: SchemaVersionResolver,
)
{
    /**
     * @throws InformationRequestTemplateValidationException when the named Schema Version cannot
     * govern typed data for this owner.
     */
    fun requireUsable(
        definition: InformationRequestTemplateDefinition,
        schemaVersionId: UUID?,
    )
    {
        val named = schemaVersionId ?: return
        try
        {
            schemaVersionResolver.requireUsablePublishedVersion(
                named,
                INFORMATION_REQUEST_SCHEMA_TARGET,
                ownerOf(definition),
            )
        }
        catch (refusal: FieldValidationException)
        {
            // Reported as a configuration refusal so one authoring surface returns one kind of
            // answer, and so the reason survives rather than becoming an opaque failure.
            throw InformationRequestTemplateValidationException(refusal.message)
        }
    }

    private fun ownerOf(definition: InformationRequestTemplateDefinition): ScopeReference =
        when (definition.scopeKind)
        {
            InformationRequestTemplateScopeKind.ORGANIZATION -> ScopeReference.Organization(
                requireNotNull(definition.scopeOrgId) {
                    "Organization-owned information request template names no organization"
                },
            )
            InformationRequestTemplateScopeKind.PERSONAL -> ScopeReference.Personal(
                requireNotNull(definition.scopeUserId) {
                    "Personally owned information request template names no person"
                },
            )
            InformationRequestTemplateScopeKind.PLATFORM -> ScopeReference.Platform
        }
}



