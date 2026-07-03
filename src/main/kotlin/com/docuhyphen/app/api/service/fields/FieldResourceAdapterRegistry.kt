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

    private lateinit var index: Map<String, FieldResourceAdapter>

    @PostConstruct
    fun init()
    {
        val map = mutableMapOf<String, FieldResourceAdapter>()
        for (adapter in adapters)
        {
            val type = adapter.resourceType
            check(type !in map) {
                "Duplicate FieldResourceAdapter for $type: " +
                    "${map[type]!!::class.qualifiedName} vs ${adapter::class.qualifiedName}"
            }
            map[type] = adapter
        }
        index = map
    }

    /** @throws FieldValidationException when no adapter is registered for [resourceType]. */
    fun adapterFor(resourceType: String): FieldResourceAdapter =
        index[resourceType]
            ?: throw FieldValidationException("Unsupported resource type for fields: $resourceType")

    fun supportedResourceTypes(): Set<String> = index.keys
}
