package com.docuhyphen.app.api.service.auth.authz

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

/**
 * States that one [ResourceKind] may be decided from the grants held on its parent resource.
 *
 * Registration is the only way a kind inherits anything. A kind with no registered policy
 * inherits nothing, even when its resolved context names a parent, so adding a parent reference
 * to a context can never silently widen a decision.
 *
 * A policy may narrow what crosses the boundary. The default keeps the parent's capabilities as
 * they are, which is correct when parent and child are decided from the same capability
 * vocabulary; a kind whose child capabilities are narrower states that here.
 */
interface ParentGrantInheritancePolicy
{
    val supportedKind: ResourceKind

    /** Narrows the capabilities a child may take from one inherited parent grant. */
    fun inheritedCapabilities(parentCapabilities: Set<Capability>): Set<Capability> = parentCapabilities
}

/**
 * The answer to whether one resource takes grants from its parent.
 */
sealed interface ParentGrantInheritance
{
    /** Nothing is inherited: no policy is registered, or the resource names no parent. */
    data object None : ParentGrantInheritance

    /**
     * Exactly one parent contributes its grants. [parentResolution] is the parent's own already
     * answered context, so the parent is resolved once per decision.
     */
    data class Inherited(
        val parent: ResourceRef,
        val parentResolution: ResourceContextResolution,
        val policy: ParentGrantInheritancePolicy,
    ) : ParentGrantInheritance

    /**
     * A registered inheritance could not be completed safely. The decision fails closed with
     * [reasonCode] rather than falling back to the child's own grants, because a parent that
     * cannot be proved to share the child's owner is not a parent this decision may use.
     */
    data class Refused(val reasonCode: String, val message: String) : ParentGrantInheritance
}

/**
 * Discovers all [ParentGrantInheritancePolicy] CDI beans at startup and indexes them by
 * [ResourceKind]. Duplicate registrations for the same kind fail fast. An instance that was
 * never initialized holds no policy, so nothing inherits.
 */
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

/**
 * Decides whether a resource takes the grants held on its parent.
 *
 * The rules are deliberately narrow:
 *
 *  1. The child's kind must have a registered [ParentGrantInheritancePolicy]. Otherwise nothing
 *     is inherited.
 *  2. The child's resolved context must name a parent. A child that names none inherits nothing.
 *  3. Inheritance is one level. The parent's own parent is never followed, so a chain cannot
 *     accumulate grants the middle resource was never given.
 *  4. A parent that is the child itself, or whose own parent is the child, is a cycle and is
 *     refused.
 *  5. The parent must resolve its own facts. A parent that cannot be resolved, or that carries no
 *     resource context at all, refuses the decision instead of contributing grants.
 *  6. Parent and child must have the same owner. Crossing an ownership boundary through a parent
 *     reference is refused.
 */
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
