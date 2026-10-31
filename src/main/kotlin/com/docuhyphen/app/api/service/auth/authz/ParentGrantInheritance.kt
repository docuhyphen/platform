package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.ParentGrantInheritance
import com.docuhyphen.app.api.model.auth.authz.ResourceContextResolution
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

interface ParentGrantInheritancePolicy
{
    val supportedKind: ResourceKind

    fun inheritedCapabilities(parentCapabilities: Set<Capability>): Set<Capability> = parentCapabilities
}

@ApplicationScoped
class ParentGrantInheritancePolicyRegistry
{
    @Inject
    @Any
    private lateinit var policies: Instance<ParentGrantInheritancePolicy>

    private var index: Map<ResourceKind, ParentGrantInheritancePolicy> = emptyMap()

    @PostConstruct
    fun init()
    {
        val map = mutableMapOf<ResourceKind, ParentGrantInheritancePolicy>()
        for (policy in policies)
        {
            val kind = policy.supportedKind
            check(kind !in map) {
                "Duplicate ParentGrantInheritancePolicy for $kind: " +
                        "${map[kind]!!::class.qualifiedName} vs ${policy::class.qualifiedName}"
            }
            map[kind] = policy
        }
        index = map
    }

    fun policyFor(kind: ResourceKind): ParentGrantInheritancePolicy? = index[kind]
}

@ApplicationScoped
class ParentGrantInheritanceResolver @Inject constructor(
    private val policyRegistry: ParentGrantInheritancePolicyRegistry,
    private val resourceContextRegistry: ResourceAuthorizationContextRegistry,
)
{
    fun evaluate(child: ResourceRef, childResolution: ResourceContextResolution): ParentGrantInheritance
    {
        val kind = resourceContextRegistry.kindOf(child)
            ?: return ParentGrantInheritance.None
        val policy = policyRegistry.policyFor(kind) ?: return ParentGrantInheritance.None

        val childContext = (childResolution as? ResourceContextResolution.Resolved)?.context
            ?: return ParentGrantInheritance.None
        val parent = childContext.parentRef ?: return ParentGrantInheritance.None

        if (parent == child)
        {
            return ParentGrantInheritance.Refused(
                Decision.REASON_PARENT_INHERITANCE_CYCLE,
                "Resource $child names itself as its authorization parent",
            )
        }

        val parentResolution = resourceContextRegistry.resolution(parent)
        val parentContext = (parentResolution as? ResourceContextResolution.Resolved)?.context
            ?: return ParentGrantInheritance.Refused(
                Decision.REASON_PARENT_CONTEXT_UNRESOLVED,
                "Authorization parent $parent of $child could not be resolved",
            )

        if (parentContext.parentRef == child)
        {
            return ParentGrantInheritance.Refused(
                Decision.REASON_PARENT_INHERITANCE_CYCLE,
                "Resources $child and $parent name each other as authorization parent",
            )
        }

        if (parentContext.ownerContext != childContext.ownerContext)
        {
            return ParentGrantInheritance.Refused(
                Decision.REASON_PARENT_OWNER_MISMATCH,
                "Authorization parent $parent has a different owner than $child",
            )
        }

        return ParentGrantInheritance.Inherited(parent, parentResolution, policy)
    }
}
