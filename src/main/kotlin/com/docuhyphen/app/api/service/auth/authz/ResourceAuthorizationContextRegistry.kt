package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.ResourceType
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

/**
 * Discovers all [ResourceAuthorizationContextProvider] CDI beans at startup and indexes
 * them by [ResourceKind]. Duplicate registrations for the same kind fail fast.
 *
 * [resolve] returns null when no provider is registered or the provider cannot locate the
 * resource — both cases fail closed in [DefaultAuthorizationService].
 */
@ApplicationScoped
class ResourceAuthorizationContextRegistry
{
    @Inject
    @Any
    private lateinit var providers: Instance<ResourceAuthorizationContextProvider>

    private lateinit var index: Map<ResourceKind, ResourceAuthorizationContextProvider>

    @PostConstruct
    fun init()
    {
        val map = mutableMapOf<ResourceKind, ResourceAuthorizationContextProvider>()
        for (provider in providers)
        {
            val kind = provider.supportedKind
            check(kind !in map) {
                "Duplicate ResourceAuthorizationContextProvider for $kind: " +
                    "${map[kind]!!::class.qualifiedName} vs ${provider::class.qualifiedName}"
            }
            map[kind] = provider
        }
        index = map
    }

    /** Resolves context for a [ResourceRef]. Returns null when unresolvable. */
    fun resolve(ref: ResourceRef): ResourceAuthorizationContext?
    {
        val kind = ref.type.toResourceKind() ?: return null
        return index[kind]?.resolve(ref.id)
    }

    /** Resolves context for a [ResourceReference]. Returns null when unresolvable. */
    fun resolve(ref: ResourceReference): ResourceAuthorizationContext? =
        index[ref.kind]?.resolve(ref.id)

    companion object
    {
        fun ResourceType.toResourceKind(): ResourceKind? = when (this)
        {
            ResourceType.EXCHANGE -> ResourceKind.EXCHANGE
            ResourceType.DOCUMENT -> ResourceKind.DOCUMENT
            ResourceType.PRINCIPAL_GROUP -> ResourceKind.PRINCIPAL_GROUP
            ResourceType.DOC_LIBRARY -> ResourceKind.DOCUMENT_LIBRARY_ENTRY
            ResourceType.BLUEPRINT -> ResourceKind.BLUEPRINT
            ResourceType.WORKFLOW_DEFINITION -> ResourceKind.WORKFLOW_DEFINITION
            ResourceType.SEQUENCE -> ResourceKind.SEQUENCE_DEFINITION
            ResourceType.VARIABLE -> ResourceKind.VARIABLE_DEFINITION
            ResourceType.COMMUNICATION -> ResourceKind.COMMUNICATION
            // Platform-owned types have no resource-state provider; resolve() returns null,
            // which skips the archived/suspended check in DefaultAuthorizationService.
            ResourceType.APPLICATION -> null
            ResourceType.WORKFLOW_WEBHOOK_ENDPOINT -> null
            ResourceType.ORGANIZATION -> ResourceKind.ORGANIZATION
        }
    }
}
