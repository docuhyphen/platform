package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.INFORMATION_REQUEST_SCHEMA_TARGET
import com.docuhyphen.app.api.service.fields.SchemaVersionResolver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestTemplateSchemaCompatibility @Inject constructor(
    private val schemaVersionResolver: SchemaVersionResolver,
)
{
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



