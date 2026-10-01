package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestParentPolicyFacts
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyEvaluator
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyOutcome
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyRequest
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentPolicy
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
