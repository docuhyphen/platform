package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.ResourceType
import jakarta.enterprise.context.ApplicationScoped

/** The Exchange, the only resource Fields governed before other targets were declared. */
internal val EXCHANGE_SCHEMA_TARGET: String = ResourceType.EXCHANGE.name

/**
 * A runtime request for information from one or more parties. It has no [ResourceType] entry
 * because the authorization stack does not know that resource yet; this is the code its Schemas
 * are stored under.
 */
internal const val INFORMATION_REQUEST_SCHEMA_TARGET: String = "INFORMATION_REQUEST"

/** Every resource type a Schema may be written for. Fixed in code, extended by adding a code here. */
internal val ALL_SCHEMA_TARGETS: Set<String> = setOf(
    EXCHANGE_SCHEMA_TARGET,
    INFORMATION_REQUEST_SCHEMA_TARGET,
)

/**
 * The resource types a Schema may be written for.
 *
 * Declaring a target says a Schema may name it. It does not say a resource of that type exists to
 * carry one: a resource becomes governable by Fields when a [FieldResourceAdapter] is installed for
 * it, and until then a Schema written for that target has nothing to be assigned to. Keeping the
 * two statements apart lets configuration be modelled before the runtime that consumes it exists.
 * The reverse is refused: an adapter whose resource type is not a declared target could never be
 * given a Schema, so indexing the adapters rejects one at startup.
 *
 * `schema_definition.target_resource_type` is an unconstrained string column, so this registry, not
 * the database, is what fails an unknown target closed.
 */
@ApplicationScoped
class SchemaTargetRegistry
{
    /** The target a Schema is written for when its author names none. */
    fun defaultTarget(): String = EXCHANGE_SCHEMA_TARGET

    fun supportedTargets(): Set<String> = ALL_SCHEMA_TARGETS

    /**
     * The target a caller asked for, normalized, or [defaultTarget] when the caller named none.
     * @throws FieldValidationException when the named target is not declared (fail closed).
     */
    fun resolveRequestedTarget(requested: String?): String
    {
        val named = requested?.trim()?.uppercase()?.ifBlank { null } ?: return defaultTarget()
        return requireDeclared(named)
    }

    /** @throws FieldValidationException when [resourceType] is not a declared target (fail closed). */
    fun requireDeclared(resourceType: String): String
    {
        if (resourceType !in ALL_SCHEMA_TARGETS)
            throw FieldValidationException("Unsupported schema target resource type: $resourceType")
        return resourceType
    }
}
