package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.auth.authz.OwnerContext
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.service.auth.authz.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class InformationRequestParentPolicyTest {
    private val requestId = UUID.randomUUID()
    private val context = ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(UUID.randomUUID()))
    private val terminatedParents = listOf(
        InformationRequestParentSnapshot(ExchangeStatus.ENDED),
        InformationRequestParentSnapshot(ExchangeStatus.REJECTED),
        InformationRequestParentSnapshot(ExchangeStatus.RESCINDED),
        InformationRequestParentSnapshot(ExchangeStatus.ACCEPTED_STARTED, deleted = true),
    )

    @Test
    fun `a retained record owner keeps every read of an organization request after its Exchange ends or is withdrawn`() {
        val retained = setOf(
            Capability.INFORMATION_REQUEST_READ,
            Capability.INFORMATION_REQUEST_EVIDENCE_READ,
            Capability.INFORMATION_REQUEST_RETAINED_OWNER_READ,
        )
        terminatedParents.forEach { parent ->
            listOf(
                Action.INFORMATION_REQUEST_VIEW,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                Action.INFORMATION_REQUEST_EVIDENCE_VIEW
            )
                .forEach { action ->
                    assertTrue(
                        evaluate(action, retained, parent) is ResourcePolicyOutcome.Permit,
                        "$action after $parent"
                    )
                }
            assertTrue(
                evaluate(
                    Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                    retained,
                    parent
                ) is ResourcePolicyOutcome.Deny, "respond after $parent"
            )
        }
    }

    @Test
    fun `an ordinary party with the same read capability is refused after rejection rescission or deletion`() {
        val party = setOf(Capability.INFORMATION_REQUEST_READ, Capability.INFORMATION_REQUEST_EVIDENCE_READ)
        terminatedParents.filter { it.status != ExchangeStatus.ENDED }.forEach { parent ->
            assertTrue(
                evaluate(Action.INFORMATION_REQUEST_VIEW, party, parent) is ResourcePolicyOutcome.Deny,
                "view after $parent"
            )
        }
    }

    @Test
    fun `the owner may still revoke a fact once its Exchange finishes but never promote one, and never after deletion`() {
        val owner = setOf(Capability.INFORMATION_REQUEST_ADMIN, Capability.INFORMATION_REQUEST_READ)
        terminatedParents.forEach { parent ->
            val revocation = evaluate(Action.INFORMATION_REQUEST_REVOKE_FACT, owner, parent)
            assertTrue(
                if (parent.deleted) revocation is ResourcePolicyOutcome.Deny else revocation is ResourcePolicyOutcome.Permit,
                "revoke after $parent"
            )
            assertTrue(
                evaluate(Action.INFORMATION_REQUEST_PROMOTE_FACT, owner, parent) is ResourcePolicyOutcome.Deny,
                "promote after $parent"
            )
        }
        assertTrue(
            evaluate(
                Action.INFORMATION_REQUEST_REVOKE_FACT,
                setOf(Capability.INFORMATION_REQUEST_READ),
                InformationRequestParentSnapshot(ExchangeStatus.ENDED),
            ) is ResourcePolicyOutcome.Deny,
            "a party without request administration after the Exchange ends"
        )
    }

    private fun evaluate(
        action: Action,
        capabilities: Set<Capability>,
        parent: InformationRequestParentSnapshot
    ): ResourcePolicyOutcome =
        InformationRequestParentPolicy.evaluate(
            ResourcePolicyRequest(
                PrincipalRef.user(UUID.randomUUID()),
                action,
                ResourceRef.informationRequest(requestId),
                context,
                capabilities,
                AuthorizationContext(),
            ),
            parent,
        )
}
