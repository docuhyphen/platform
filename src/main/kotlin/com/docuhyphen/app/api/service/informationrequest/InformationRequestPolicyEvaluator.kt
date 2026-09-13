package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestParentPolicyFacts
import com.docuhyphen.app.api.service.auth.authz.*
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InformationRequestPolicyEvaluator : ResourcePolicyEvaluator
{
    override val supportedKind = ResourceKind.INFORMATION_REQUEST

    override fun evaluate(request: ResourcePolicyRequest): ResourcePolicyOutcome =
        (request.resourceContext.policyFacts as? InformationRequestParentPolicyFacts)?.let {
            InformationRequestParentPolicy.evaluate(request, it.parent)
        } ?: ResourcePolicyOutcome.FactsUnavailable("Parent Exchange state is unavailable")
}
