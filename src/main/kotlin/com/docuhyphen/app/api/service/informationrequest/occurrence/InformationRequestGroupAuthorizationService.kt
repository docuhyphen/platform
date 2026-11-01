package com.docuhyphen.app.api.service.informationrequest.occurrence

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.ResourcePolicyOutcome
import com.docuhyphen.app.api.model.auth.authz.ResourcePolicyRequest
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementPolicyFacts
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementPolicyEvaluator
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestGroupAuthorizationService @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val contextProvider: InformationRequestRequirementAuthorizationContextProvider,
    private val requirementPolicyEvaluator: InformationRequestRequirementPolicyEvaluator,
)
{
    fun authorizeGroupCreation(
        access: RequestAccessContext,
        request: InformationRequest,
        materializedBindings: Collection<InformationRequestTemplateRequirementBinding>,
        groupBindings: Collection<InformationRequestTemplateRequirementBinding>,
    ) {
        val scope = materializedBindings.ifEmpty { groupBindings }.distinctBy { it.id }
        if (scope.isEmpty()) throw changeRefused()
        scope.forEach { binding -> authorizeAgainstAuthoredBinding(access, request, binding) }
    }

    fun authorizeGroupChange(
        access: RequestAccessContext,
        request: InformationRequest,
        groupBindings: Collection<InformationRequestTemplateRequirementBinding>,
    ) {
        val authored = groupBindings.distinctBy { it.id }
        if (authored.isEmpty()) throw changeRefused()
        authored.forEach { binding -> authorizeAgainstAuthoredBinding(access, request, binding) }
    }

    fun permitsGroupCreation(
        access: RequestAccessContext,
        request: InformationRequest,
        materializedBindings: Collection<InformationRequestTemplateRequirementBinding>,
        groupBindings: Collection<InformationRequestTemplateRequirementBinding>,
    ): Boolean = try {
        authorizeGroupCreation(access, request, materializedBindings, groupBindings)
        true
    } catch (_: ForbiddenException) {
        false
    }

    fun authorizeOccurrenceChange(
        access: RequestAccessContext,
        request: InformationRequest,
        occurrencePaths: Set<String>,
        groupBindings: Collection<InformationRequestTemplateRequirementBinding>,
    )
    {
        val held = activeRequirements(request.id).filter { requirement ->
            occurrencePaths.any { InformationRequestOccurrencePath.isWithin(requirement.occurrencePath, it) }
        }
        val authored = groupBindings.distinctBy { it.id }
        if (held.isEmpty() && authored.isEmpty()) throw changeRefused()
        held.forEach { requirement -> authorizeAgainstRequirement(access, requirement.id) }
        authored.forEach { binding -> authorizeAgainstAuthoredBinding(access, request, binding) }
    }

    fun visibleOccurrences(
        access: RequestAccessContext,
        requestId: UUID,
        occurrences: List<InformationRequestGroupOccurrence>,
    ): List<InformationRequestGroupOccurrence>
    {
        if (occurrences.isEmpty()) return occurrences
        val held = activeRequirements(requestId).filter { requirement ->
            occurrences.any { InformationRequestOccurrencePath.isWithin(requirement.occurrencePath, it.occurrencePath) }
        }
        val disclosedPaths = held.filter { mayRead(access, it.id) }.map { it.occurrencePath }
        return occurrences.filter { occurrence ->
            val within = { path: String -> InformationRequestOccurrencePath.isWithin(path, occurrence.occurrencePath) }
            disclosedPaths.any(within) || held.none { within(it.occurrencePath) }
        }
    }

    private fun activeRequirements(requestId: UUID) =
        occurrenceRepository.findForRequest(requestId).map { it.occurrencePath }.toSet().let { activePaths ->
            requirementRepository.findForRequest(requestId).filter { requirement ->
                InformationRequestOccurrencePath.isActiveOccurrence(requirement.occurrencePath, activePaths)
            }
        }

    private fun mayRead(access: RequestAccessContext, requirementId: UUID): Boolean =
        READ_ACTIONS.any { action ->
            authorizationService.authorize(
                access.principal,
                action,
                ResourceRef.informationRequestRequirement(requirementId),
                access.authorization,
            ) !is Decision.Deny
        }

    private fun authorizeAgainstRequirement(access: RequestAccessContext, requirementId: UUID)
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        )
        if (decision is Decision.Deny) throw changeRefused()
    }

    private fun authorizeAgainstAuthoredBinding(
        access: RequestAccessContext,
        request: InformationRequest,
        binding: InformationRequestTemplateRequirementBinding,
    )
    {
        val context = contextProvider.authoredContextFor(request, binding) ?: throw changeRefused()
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
        if (outcome !is ResourcePolicyOutcome.Permit) throw changeRefused()
    }

    private fun changeRefused() = ForbiddenException(CHANGE_REFUSAL)

    companion object {
        private val READ_ACTIONS =
            listOf(Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND)
        const val CHANGE_REFUSAL = "Access denied to change Information Request group occurrences"
    }
}
