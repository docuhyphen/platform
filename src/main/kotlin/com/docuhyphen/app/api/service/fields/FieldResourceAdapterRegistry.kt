package com.docuhyphen.app.api.service.fields

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

/**
 * Discovers all [FieldResourceAdapter] CDI beans at startup and indexes them by resource type.
 * Duplicate registrations for the same type fail fast. Adding a new governed resource means adding
 * an adapter, not changing the field model.
 */
@ApplicationScoped
class FieldResourceAdapterRegistry
{
    @Inject
    @Any
    private lateinit var adapters: Instance<FieldResourceAdapter>

    @Inject
    private lateinit var schemaTargets: SchemaTargetRegistry

    private lateinit var index: Map<String, FieldResourceAdapter>

    @PostConstruct
    fun init()
    {
        index = indexAdapters(adapters, schemaTargets)
    }

    /** @throws FieldValidationException when no adapter is registered for [resourceType]. */
    fun adapterFor(resourceType: String): FieldResourceAdapter =
        index[resourceType]
            ?: throw FieldValidationException("Unsupported resource type for fields: $resourceType")

    fun supportedResourceTypes(): Set<String> = index.keys
}

/**
 * Indexes adapters by the resource type each one governs. Both refusals here are startup failures
 * because neither leaves a working installation: two adapters claiming one type leaves no answer to
 * which of them governs it, and an adapter governing a type no Schema may be written for could never
 * be given one, since an assignment requires a Schema written for exactly that type.
 */
internal fun indexAdapters(
    adapters: Iterable<FieldResourceAdapter>,
    schemaTargets: SchemaTargetRegistry,
): Map<String, FieldResourceAdapter>
{
    val map = mutableMapOf<String, FieldResourceAdapter>()
    for (adapter in adapters)
    {
        val type = adapter.resourceType
        check(type !in map) {
            "Duplicate FieldResourceAdapter for $type: " +
                    "${map[type]!!::class.qualifiedName} vs ${adapter::class.qualifiedName}"
        }
        check(type in schemaTargets.supportedTargets()) {
            "FieldResourceAdapter ${adapter::class.qualifiedName} governs $type, " +
                    "which is not a resource type a schema may be written for"
        }
        map[type] = adapter
    }
    return map
}
