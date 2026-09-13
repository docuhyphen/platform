package com.docuhyphen.app.api.service.auth.authz

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

/**
 * The facts one resource kind supplies to its own policy evaluator, after the central stack has
 * already decided which capabilities the principal holds on the resource.
 */
data class ResourcePolicyRequest(
    val principal: PrincipalRef,
    val action: Action,
    val resource: ResourceRef,
    val resourceContext: ResourceAuthorizationContext,
    val capabilities: Set<Capability>,
    val authorizationContext: AuthorizationContext,
)

/** What a resource-kind policy evaluator may say about one already-capable decision. */
sealed interface ResourcePolicyOutcome
{
    /** The kind adds nothing beyond the obligations it states. */
    data class Permit(val obligations: ShareObligations = ShareObligations()) : ResourcePolicyOutcome

    /** The kind's own facts forbid the action, with a stable machine reason. */
    data class Deny(val reasonCode: String, val message: String) : ResourcePolicyOutcome

    /**
     * The evaluator could not read the facts it needs. The decision is refused rather than made
     * without them.
     */
    data class FactsUnavailable(val message: String) : ResourcePolicyOutcome
}

/**
 * Narrows an allowed decision using facts that only one [ResourceKind] understands, such as which
 * party an occurrence nominates, which confidentiality compartment it falls into, or whether it
 * is inside an open correction scope.
 *
 * An evaluator may only deny or attach obligations. It can never turn a decision the central
 * capability union refused into an allowed one, so a kind cannot grow a private allowance path
 * beside the central stack. A kind with no registered evaluator adds nothing.
 */
interface ResourcePolicyEvaluator
{
    val supportedKind: ResourceKind

    fun evaluate(request: ResourcePolicyRequest): ResourcePolicyOutcome
}

/**
 * Discovers all [ResourcePolicyEvaluator] CDI beans at startup and indexes them by
 * [ResourceKind]. Duplicate registrations for the same kind fail fast. An instance that was never
 * initialized holds no evaluator, so no kind narrows anything.
 */
@ApplicationScoped
class ResourcePolicyEvaluatorRegistry
{
    @Inject
    @Any
    private lateinit var evaluators: Instance<ResourcePolicyEvaluator>

    private var index: Map<ResourceKind, ResourcePolicyEvaluator> = emptyMap()

    @PostConstruct
    fun init()
    {
        val map = mutableMapOf<ResourceKind, ResourcePolicyEvaluator>()
        for (evaluator in evaluators)
        {
            val kind = evaluator.supportedKind
            check(kind !in map) {
                "Duplicate ResourcePolicyEvaluator for $kind: " +
                    "${map[kind]!!::class.qualifiedName} vs ${evaluator::class.qualifiedName}"
            }
            map[kind] = evaluator
        }
        index = map
    }

    fun evaluatorFor(kind: ResourceKind): ResourcePolicyEvaluator? = index[kind]
}
