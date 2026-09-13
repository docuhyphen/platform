package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyOutcome
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyRequest
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestGroupAuthorizationService @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val contextProvider: InformationRequestRequirementAuthorizationContextProvider,
    private val requirementPolicyEvaluator: InformationRequestRequirementPolicyEvaluator,
)
{
    fun authorizeOccurrenceScope(
        access: RequestAccessContext,
        requestId: UUID,
        occurrencePaths: Set<String>,
    )
    {
        if (occurrencePaths.isEmpty()) return
        requirementRepository.findForRequest(requestId)
            .filter { it.occurrencePath in occurrencePaths }
            .forEach { requirement -> authorizeAgainstRequirement(access, requirement.id) }
    }

    fun authorizeMaterializedBindings(
        access: RequestAccessContext,
        request: InformationRequest,
        bindings: Collection<InformationRequestTemplateRequirementBinding>,
    )
    {
        val existingByBindingId = requirementRepository.findForRequest(request.id)
            .associateBy { it.sourceTemplateBindingId }
        bindings.distinctBy { it.id }.forEach { binding ->
            val existing = existingByBindingId[binding.id]
            if (existing != null)
            {
                authorizeAgainstRequirement(access, existing.id)
            }
            else
            {
                authorizeAgainstAuthoredBinding(access, request, binding)
            }
        }
    }

    private fun authorizeAgainstRequirement(access: RequestAccessContext, requirementId: UUID)
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Access denied to change this Information Request group occurrence")
        }
    }

    private fun authorizeAgainstAuthoredBinding(
        access: RequestAccessContext,
        request: InformationRequest,
        binding: InformationRequestTemplateRequirementBinding,
    )
    {
        val context = contextProvider.authoredContextFor(request, binding)
            ?: throw ForbiddenException("Access denied to change Information Request group occurrences")
        val facts = context.policyFacts as InformationRequestRequirementPolicyFacts
        val outcome = requirementPolicyEvaluator.evaluate(
            ResourcePolicyRequest(
                principal = access.principal,
                action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                resource = ResourceRef.informationRequestRequirement(facts.requirementId),
                resourceContext = context,
                capabilities = emptySet(),
                authorizationContext = access.authorization,
            ),
        )
        if (outcome !is ResourcePolicyOutcome.Permit)
        {
            throw ForbiddenException("Access denied to change Information Request group occurrences")
        }
    }
}
