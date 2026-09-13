package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyEvaluator
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyOutcome
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyRequest
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InformationRequestRequirementPolicyEvaluator : ResourcePolicyEvaluator
{
    override val supportedKind: ResourceKind = ResourceKind.INFORMATION_REQUEST_REQUIREMENT

    override fun evaluate(request: ResourcePolicyRequest): ResourcePolicyOutcome =
        when (val facts = request.resourceContext.policyFacts)
        {
            is InformationRequestRequirementPolicyFacts -> evaluateWithFacts(request, facts)
            else -> ResourcePolicyOutcome.FactsUnavailable(
                "Requirement policy facts are unavailable for ${request.resource}",
            )
        }

    private fun evaluateWithFacts(
        request: ResourcePolicyRequest,
        facts: InformationRequestRequirementPolicyFacts,
    ): ResourcePolicyOutcome
    {
        val parentDecision = InformationRequestParentPolicy.evaluate(request, facts.parent)
        if (parentDecision !is ResourcePolicyOutcome.Permit) return parentDecision
        if (request.resource.id != facts.requirementId)
        {
            return ResourcePolicyOutcome.FactsUnavailable(
                "Requirement policy facts do not match ${request.resource.id}",
            )
        }

        if (facts.confidentialityCompartmentKey != null && !request.authorizationContext.mfaSatisfied)
        {
            return deny(
                InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED,
                "Requirement ${facts.requirementId} is in a protected confidentiality compartment",
            )
        }

        if (facts.responseMode == InformationRequestResponseMode.NOT_DISCLOSED &&
            request.action in partyDisclosureActions)
        {
            return deny(
                InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED,
                "Requirement ${facts.requirementId} is not disclosed to the nominated party",
            )
        }

        if (request.action in exactPartyActions && !facts.canActAsAssignedParty(request.principal))
        {
            return deny(
                InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED,
                "Principal ${request.principal.kind}/${request.principal.id} is not assigned to requirement " +
                    facts.requirementId,
            )
        }

        if (request.action in answerMutationActions && facts.occurrenceRemoved)
        {
            return deny(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED,
                "Requirement ${facts.requirementId} belongs to a removed group occurrence",
            )
        }

        if (request.action in answerMutationActions && facts.responseMode !in answerableResponseModes)
        {
            return deny(
                InformationRequestErrorCatalog.RESPONSE_MODE_DENIED,
                "Requirement ${facts.requirementId} response mode ${facts.responseMode} does not permit mutation",
            )
        }

        if (request.action in answerMutationActions && facts.requestState !in activeResponseStates)
        {
            return deny(
                InformationRequestErrorCatalog.STATE_INVALID,
                "Request ${facts.requestId} state ${facts.requestState} does not accept response mutations",
            )
        }

        if (request.action in answerMutationActions &&
            facts.correctionScope == InformationRequestRequirementCorrectionScope.OPEN_CORRECTION)
        {
            return deny(
                InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED,
                "Requirement ${facts.requirementId} is in correction state without item scope",
            )
        }

        return ResourcePolicyOutcome.Permit()
    }

    private fun InformationRequestRequirementPolicyFacts.canActAsAssignedParty(
        principal: PrincipalRef,
    ): Boolean =
        assignedParties.any { party ->
            party.roleKey == assignedRoleKey &&
                (
                    party.principal == principal ||
                        principal in party.equivalentPrincipals ||
                        hasActiveAuthorityFor(principal, party.partyId)
                    )
        }

    private fun InformationRequestRequirementPolicyFacts.hasActiveAuthorityFor(
        principal: PrincipalRef,
        assignedPartyId: java.util.UUID,
    ): Boolean =
        delegatedAuthorityFacts.any { authority ->
            authority.active &&
                authority.assignedPartyId == assignedPartyId &&
                authority.delegatePrincipal == principal &&
                authority.requestId == requestId &&
                (authority.requirementId == null || authority.requirementId == requirementId)
        }

    private fun deny(reasonCode: String, message: String): ResourcePolicyOutcome.Deny =
        ResourcePolicyOutcome.Deny(reasonCode, message)

    private companion object
    {
        val partyDisclosureActions = setOf(
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            Action.INFORMATION_REQUEST_EVIDENCE_VIEW,
        )

        val answerMutationActions = setOf(
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST,
            Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD,
            Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW,
        )

        val exactPartyActions = partyDisclosureActions + answerMutationActions

        val answerableResponseModes = setOf(
            InformationRequestResponseMode.PROVIDE,
            InformationRequestResponseMode.PROVIDE_ONCE,
        )

        val activeResponseStates = setOf(
            InformationRequestState.ISSUED,
            InformationRequestState.IN_PROGRESS,
            InformationRequestState.CHANGES_REQUESTED,
        )
    }
}
