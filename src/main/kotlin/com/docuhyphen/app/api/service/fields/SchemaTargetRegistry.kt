package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.ResourceType
import jakarta.enterprise.context.ApplicationScoped

internal val EXCHANGE_SCHEMA_TARGET: String = ResourceType.EXCHANGE.name

internal val INFORMATION_REQUEST_SCHEMA_TARGET: String = ResourceType.INFORMATION_REQUEST.name

internal val ALL_SCHEMA_TARGETS: Set<String> = setOf(
    EXCHANGE_SCHEMA_TARGET,
    INFORMATION_REQUEST_SCHEMA_TARGET,
)

@ApplicationScoped
class SchemaTargetRegistry
{
    fun defaultTarget(): String = EXCHANGE_SCHEMA_TARGET

    fun supportedTargets(): Set<String> = ALL_SCHEMA_TARGETS

    fun resolveRequestedTarget(requested: String?): String
    {
        val named = requested?.trim()?.uppercase()?.ifBlank { null } ?: return defaultTarget()
        return requireDeclared(named)
    }

    fun requireDeclared(resourceType: String): String
    {
        if (resourceType !in ALL_SCHEMA_TARGETS)
            throw FieldValidationException("Unsupported schema target resource type: $resourceType")
        return resourceType
    }
}
