package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.ResourceType
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

/**
 * The three answers the registry can give about one resource reference.
 *
 * A resource type either declares that it carries its own authorization facts or declares that
 * it carries none. The first must produce those facts before a decision can be made; the second
 * is a platform control-plane reference whose decision never depended on resource state.
 */
sealed interface ResourceContextResolution
{
    /** The resource's own facts were produced. */
    data class Resolved(val context: ResourceAuthorizationContext) : ResourceContextResolution

    /**
     * The resource type declares its own facts, but they could not be produced: no provider is
     * installed for the kind, or the provider could not locate the resource or its owner.
     * Callers must refuse rather than decide the resource from grants scoped to something else.
     */
    data object Unresolved : ResourceContextResolution

    /**
     * The resource type deliberately carries no resource-level context. Decisions about it rest
     * on platform and organization roles alone.
     */
    data object NotGoverned : ResourceContextResolution
}

/**
 * Discovers all [ResourceAuthorizationContextProvider] CDI beans at startup and indexes
 * them by [ResourceKind]. Duplicate registrations for the same kind fail fast.
 *
 * [resolve] returns null when no provider is registered or the provider cannot locate the
 * resource. [resolution] separates those two cases from a type that carries no context at all,
 * which is the distinction [DefaultAuthorizationService] needs in order to fail closed.
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

    /**
     * Answers whether [ref] carries its own authorization facts and, when it does, whether those
     * facts could be produced.
     */
    fun resolution(ref: ResourceRef): ResourceContextResolution
    {
        val kind = ref.type.toResourceKind() ?: return ResourceContextResolution.NotGoverned
        val provider = index[kind] ?: return ResourceContextResolution.Unresolved
        val context = provider.resolve(ref.id) ?: return ResourceContextResolution.Unresolved
        return ResourceContextResolution.Resolved(context)
    }

    /**
     * The governed kind of [ref], or null when the type deliberately carries no resource-level
     * context.
     */
    fun kindOf(ref: ResourceRef): ResourceKind? = ref.type.toResourceKind()

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
            // Platform control-plane types carry no resource-level context. They map to no kind,
            // so a decision about one rests on platform and organization roles alone.
            ResourceType.APPLICATION -> null
            ResourceType.WORKFLOW_WEBHOOK_ENDPOINT -> null
            ResourceType.ORGANIZATION -> ResourceKind.ORGANIZATION
            ResourceType.INFORMATION_REQUEST -> ResourceKind.INFORMATION_REQUEST
            ResourceType.INFORMATION_REQUEST_PARTY -> null
            ResourceType.INFORMATION_REQUEST_DELEGATED_AUTHORITY -> null
            ResourceType.INFORMATION_REQUEST_REQUIREMENT -> ResourceKind.INFORMATION_REQUEST_REQUIREMENT
            ResourceType.INFORMATION_REQUEST_ACCESS_LINK -> null
            ResourceType.INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK -> null
            ResourceType.INFORMATION_REQUEST_EVIDENCE_ARTIFACT -> null
            ResourceType.INFORMATION_REQUEST_SUBMISSION_PACKAGE -> null
            ResourceType.INFORMATION_REQUEST_SUBMISSION_ATTESTATION -> null
            ResourceType.INFORMATION_REQUEST_AMENDMENT -> null
        }
    }
}
