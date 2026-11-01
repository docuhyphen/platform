package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.ResourcePolicyOutcome
import com.docuhyphen.app.api.model.auth.authz.ResourcePolicyRequest
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

interface ResourcePolicyEvaluator
{
    val supportedKind: ResourceKind

    fun evaluate(request: ResourcePolicyRequest): ResourcePolicyOutcome
}

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
