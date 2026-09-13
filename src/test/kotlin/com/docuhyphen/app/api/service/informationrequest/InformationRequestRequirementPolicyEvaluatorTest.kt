package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyFacts
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyOutcome
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyRequest
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class InformationRequestRequirementPolicyEvaluatorTest
{
    private val evaluator = InformationRequestRequirementPolicyEvaluator()
    private val ownerOrgId = UUID.randomUUID()
    private val requestId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val assignedPartyId = UUID.randomUUID()
    private val assignedPrincipal = PrincipalRef.user(UUID.randomUUID())
    private val linkedPrincipal = PrincipalRef.user(UUID.randomUUID())
    private val groupMemberPrincipal = PrincipalRef.user(UUID.randomUUID())
    private val delegatePrincipal = PrincipalRef.user(UUID.randomUUID())
    private val otherPrincipal = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `assigned provider may respond to an answerable Requirement`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(responseMode = InformationRequestResponseMode.PROVIDE),
        )

        assertTrue(outcome is ResourcePolicyOutcome.Permit)
    }

    @Test
    fun `response mutations require the exact assigned party`()
    {
        val outcome = evaluate(
            principal = otherPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(responseMode = InformationRequestResponseMode.PROVIDE),
        )

        assertDenied(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, outcome)
    }

    @Test
    fun `delegate cannot respond for an assigned party without active authority facts`()
    {
        val outcome = evaluate(
            principal = delegatePrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(
                delegatedAuthorityFacts = listOf(
                    delegatedAuthorityFact(active = false),
                ),
            ),
        )

        assertDenied(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, outcome)
    }

    @Test
    fun `delegate may respond for an assigned party with active scoped authority facts`()
    {
        val outcome = evaluate(
            principal = delegatePrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(
                delegatedAuthorityFacts = listOf(
                    delegatedAuthorityFact(),
                ),
            ),
        )

        assertTrue(outcome is ResourcePolicyOutcome.Permit)
    }

    @Test
    fun `linked registered User may respond for a participant assigned party`()
    {
        val outcome = evaluate(
            principal = linkedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(
                assignedPrincipal = PrincipalRef.participant(UUID.randomUUID()),
                equivalentPrincipals = setOf(linkedPrincipal),
            ),
        )

        assertTrue(outcome is ResourcePolicyOutcome.Permit)
    }

    @Test
    fun `linked registered User may view a participant assigned Requirement`()
    {
        val outcome = evaluate(
            principal = linkedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            facts = facts(
                assignedPrincipal = PrincipalRef.participant(UUID.randomUUID()),
                equivalentPrincipals = setOf(linkedPrincipal),
            ),
        )

        assertTrue(outcome is ResourcePolicyOutcome.Permit)
    }

    @Test
    fun `active group member may respond for an assigned group party`()
    {
        val outcome = evaluate(
            principal = groupMemberPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(
                assignedPrincipal = PrincipalRef.group(UUID.randomUUID()),
                equivalentPrincipals = setOf(groupMemberPrincipal),
            ),
        )

        assertTrue(outcome is ResourcePolicyOutcome.Permit)
    }

    @Test
    fun `removed group member is denied when not present in current assignment facts`()
    {
        val outcome = evaluate(
            principal = groupMemberPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(
                assignedPrincipal = PrincipalRef.group(UUID.randomUUID()),
                equivalentPrincipals = emptySet(),
            ),
        )

        assertDenied(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, outcome)
    }

    @Test
    fun `a removed group occurrence's Requirement denies response mutation`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(occurrenceRemoved = true),
        )

        assertDenied(InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED, outcome)
    }

    @Test
    fun `a removed group occurrence's Requirement still permits a historical view`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            facts = facts(occurrenceRemoved = true, responseMode = InformationRequestResponseMode.PROVIDE),
        )

        assertTrue(outcome is ResourcePolicyOutcome.Permit)
    }

    @Test
    fun `view-only response mode denies response mutation`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(responseMode = InformationRequestResponseMode.VIEW_ONLY),
        )

        assertDenied(InformationRequestErrorCatalog.RESPONSE_MODE_DENIED, outcome)
    }

    @Test
    fun `not disclosed response mode denies assigned party disclosure`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            facts = facts(responseMode = InformationRequestResponseMode.NOT_DISCLOSED),
        )

        assertDenied(InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED, outcome)
    }

    @Test
    fun `confidential Requirements require a stepped-up context`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            facts = facts(confidentialityCompartmentKey = "restricted-response"),
            authorizationContext = AuthorizationContext(mfaSatisfied = false),
        )

        assertDenied(InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED, outcome)
    }

    @Test
    fun `open correction scope blocks broad inherited response mutations until item scope exists`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(correctionScope = InformationRequestRequirementCorrectionScope.OPEN_CORRECTION),
        )

        assertDenied(InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED, outcome)
    }

    @Test
    fun `response mutation is denied while the request is submitted awaiting review`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(requestState = InformationRequestState.SUBMITTED),
        )

        assertDenied(InformationRequestErrorCatalog.STATE_INVALID, outcome)
    }

    @Test
    fun `response mutation is denied while the request is under review`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            facts = facts(requestState = InformationRequestState.UNDER_REVIEW),
        )

        assertDenied(InformationRequestErrorCatalog.STATE_INVALID, outcome)
    }

    @Test
    fun `viewing a Requirement is not blocked by request state`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            facts = facts(requestState = InformationRequestState.SUBMITTED),
        )

        assertTrue(outcome is ResourcePolicyOutcome.Permit)
    }

    @Test
    fun `missing Requirement policy facts fail closed`()
    {
        val outcome = evaluate(
            principal = assignedPrincipal,
            action = Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            facts = ResourcePolicyFacts.None,
        )

        assertTrue(outcome is ResourcePolicyOutcome.FactsUnavailable)
    }

    private fun evaluate(
        principal: PrincipalRef,
        action: Action,
        facts: ResourcePolicyFacts,
        authorizationContext: AuthorizationContext = AuthorizationContext(mfaSatisfied = true),
    ): ResourcePolicyOutcome =
        evaluator.evaluate(
            ResourcePolicyRequest(
                principal = principal,
                action = action,
                resource = ResourceRef(ResourceType.INFORMATION_REQUEST_REQUIREMENT, requirementId),
                resourceContext = ResourceAuthorizationContext(
                    ownerContext = OwnerContext.Organization(ownerOrgId),
                    policyFacts = facts,
                ),
                capabilities = setOf(action.required, Capability.INFORMATION_REQUEST_READ),
                authorizationContext = authorizationContext,
            ),
        )

    private fun facts(
        responseMode: InformationRequestResponseMode = InformationRequestResponseMode.PROVIDE,
        confidentialityCompartmentKey: String? = null,
        correctionScope: InformationRequestRequirementCorrectionScope =
            InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE,
        delegatedAuthorityFacts: List<InformationRequestRequirementDelegatedAuthorityFact> = emptyList(),
        requestState: InformationRequestState = InformationRequestState.ISSUED,
        occurrenceRemoved: Boolean = false,
        assignedPrincipal: PrincipalRef = this.assignedPrincipal,
        equivalentPrincipals: Set<PrincipalRef> = emptySet(),
    ) = InformationRequestRequirementPolicyFacts(
        requestId = requestId,
        requirementId = requirementId,
        currentRevisionId = UUID.randomUUID(),
        currentRevisionNumber = 1,
        sourceTemplateBindingId = UUID.randomUUID(),
        occurrencePath = "root",
        responseMode = responseMode,
        assignedRoleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
        assignedParties = listOf(
            InformationRequestRequirementAssignedPartyFact(
                partyId = assignedPartyId,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                principal = assignedPrincipal,
                equivalentPrincipals = equivalentPrincipals,
                subjectIdentityRefId = null,
                exchangeRecipientId = UUID.randomUUID(),
                shareId = UUID.randomUUID(),
            ),
        ),
        delegatedAuthorityFacts = delegatedAuthorityFacts,
        confidentialityCompartmentKey = confidentialityCompartmentKey,
        correctionScope = correctionScope,
        requestState = requestState,
        parent = InformationRequestParentSnapshot(com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED),
        occurrenceRemoved = occurrenceRemoved,
    )

    private fun delegatedAuthorityFact(
        active: Boolean = true,
        requestScopeId: UUID = requestId,
        requirementScopeId: UUID? = requirementId,
    ) = InformationRequestRequirementDelegatedAuthorityFact(
        authorityId = UUID.randomUUID(),
        assignedPartyId = assignedPartyId,
        delegatePrincipal = delegatePrincipal,
        requestId = requestScopeId,
        requirementId = requirementScopeId,
        active = active,
    )

    private fun assertDenied(expectedCode: String, outcome: ResourcePolicyOutcome)
    {
        assertTrue(outcome is ResourcePolicyOutcome.Deny)
        assertEquals(expectedCode, (outcome as ResourcePolicyOutcome.Deny).reasonCode)
    }
}
