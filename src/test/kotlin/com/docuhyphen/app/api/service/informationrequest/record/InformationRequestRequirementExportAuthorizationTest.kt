package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.model.auth.authz.OwnerContext
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementCorrectionScope
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementPolicyFacts
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementPolicyEvaluator
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentPolicy
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestRequirementParentGrantInheritancePolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestRequirementExportAuthorizationTest {
    private val evaluator = InformationRequestRequirementPolicyEvaluator()
    private val requirementId = UUID.randomUUID()
    private val exporter = PrincipalRef.user(UUID.randomUUID())

    @Test
    fun `a request export grant reaches the request's Requirements while administration does not`() {
        val inherited = InformationRequestRequirementParentGrantInheritancePolicy().inheritedCapabilities(
            setOf(
                Capability.INFORMATION_REQUEST_EXPORT,
                Capability.INFORMATION_REQUEST_ADMIN,
                Capability.INFORMATION_REQUEST_READ
            ),
        )

        assertEquals(setOf(Capability.INFORMATION_REQUEST_EXPORT, Capability.INFORMATION_REQUEST_READ), inherited)
    }

    @Test
    fun `exporting a Requirement is a read its exporter keeps after the parent Exchange is rejected`() {
        assertTrue(Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT in InformationRequestParentPolicy.readActions)

        val outcome = evaluate(compartmentKey = null, verified = false, parent = ExchangeStatus.REJECTED)

        assertTrue(outcome is ResourcePolicyOutcome.Permit, outcome.toString())
    }

    @Test
    fun `a Requirement in a confidentiality compartment is exported only with recent verification`() {
        val unverified = evaluate(compartmentKey = "restricted-records", verified = false)
        val verified = evaluate(compartmentKey = "restricted-records", verified = true)
        val open = evaluate(compartmentKey = null, verified = false)

        assertEquals(
            InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED,
            (unverified as ResourcePolicyOutcome.Deny).reasonCode
        )
        assertTrue(verified is ResourcePolicyOutcome.Permit, verified.toString())
        assertTrue(open is ResourcePolicyOutcome.Permit, open.toString())
    }

    private fun evaluate(
        compartmentKey: String?,
        verified: Boolean,
        parent: ExchangeStatus = ExchangeStatus.ACCEPTED_STARTED,
    ): ResourcePolicyOutcome =
        evaluator.evaluate(
            ResourcePolicyRequest(
                principal = exporter,
                action = Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT,
                resource = ResourceRef(ResourceType.INFORMATION_REQUEST_REQUIREMENT, requirementId),
                resourceContext = ResourceAuthorizationContext(
                    ownerContext = OwnerContext.Organization(UUID.randomUUID()),
                    policyFacts = InformationRequestRequirementPolicyFacts(
                        requestId = UUID.randomUUID(),
                        requirementId = requirementId,
                        currentRevisionId = UUID.randomUUID(),
                        currentRevisionNumber = 1,
                        sourceTemplateBindingId = UUID.randomUUID(),
                        occurrencePath = "root",
                        responseMode = InformationRequestResponseMode.NOT_DISCLOSED,
                        assignedRoleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                        assignedParties = emptyList(),
                        confidentialityCompartmentKey = compartmentKey,
                        correctionScope = InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE,
                        requestState = InformationRequestState.CLOSED,
                        parent = InformationRequestParentSnapshot(parent),
                    ),
                ),
                capabilities = setOf(Capability.INFORMATION_REQUEST_EXPORT, Capability.INFORMATION_REQUEST_READ),
                authorizationContext = AuthorizationContext(mfaSatisfied = verified),
            ),
        )
}
