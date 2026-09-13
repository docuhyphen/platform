package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestDelegatedAuthorityFactSourceTest
{
    @Test
    fun `active authority facts are limited to assigned parties and current Requirement scope`()
    {
        val requestId = UUID.randomUUID()
        val requirementId = UUID.randomUUID()
        val assignedPartyId = UUID.randomUUID()
        val delegatePrincipal = PrincipalRef.user(UUID.randomUUID())
        val requestWideAuthority = authority(
            requestId = requestId,
            assignedPartyId = assignedPartyId,
            delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
            requirementId = null,
        )
        val exactRequirementAuthority = authority(
            requestId = requestId,
            assignedPartyId = assignedPartyId,
            delegatePrincipal = delegatePrincipal,
            requirementId = requirementId,
        )
        val inactiveAuthority = authority(
            requestId = requestId,
            assignedPartyId = assignedPartyId,
            delegatePrincipal = PrincipalRef.user(UUID.randomUUID()),
            requirementId = requirementId,
            active = false,
        )
        val unrelatedPartyAuthority = authority(
            requestId = requestId,
            assignedPartyId = UUID.randomUUID(),
            delegatePrincipal = PrincipalRef.user(UUID.randomUUID()),
            requirementId = requirementId,
        )
        val outOfScopeRequirementAuthority = authority(
            requestId = requestId,
            assignedPartyId = assignedPartyId,
            delegatePrincipal = PrincipalRef.user(UUID.randomUUID()),
            requirementId = UUID.randomUUID(),
        )
        val unrelatedRequestAuthority = authority(
            requestId = UUID.randomUUID(),
            assignedPartyId = assignedPartyId,
            delegatePrincipal = PrincipalRef.user(UUID.randomUUID()),
            requirementId = requirementId,
        )
        val notYetEffectiveAuthority = authority(
            requestId = requestId,
            assignedPartyId = assignedPartyId,
            delegatePrincipal = PrincipalRef.user(UUID.randomUUID()),
            requirementId = requirementId,
            effectiveAt = Timestamp.from(Instant.now().plusSeconds(3600)),
        )
        val expiredAuthority = authority(
            requestId = requestId,
            assignedPartyId = assignedPartyId,
            delegatePrincipal = PrincipalRef.user(UUID.randomUUID()),
            requirementId = requirementId,
            expiresAt = Timestamp.from(Instant.now().minusSeconds(3600)),
        )
        val repository = mock<InformationRequestDelegatedAuthorityRepository>()
        whenever(repository.findForRequest(eq(requestId))).thenReturn(
            listOf(
                inactiveAuthority,
                unrelatedPartyAuthority,
                outOfScopeRequirementAuthority,
                unrelatedRequestAuthority,
                notYetEffectiveAuthority,
                expiredAuthority,
                requestWideAuthority,
                exactRequirementAuthority,
            ),
        )
        val source = InformationRequestDelegatedAuthorityFactSource(repository)

        val facts = source.factsFor(
            requestId = requestId,
            requirementId = requirementId,
            assignedPartyIds = setOf(assignedPartyId),
        )

        assertEquals(
            listOf(
                requestWideAuthority.toExpectedFact(),
                exactRequirementAuthority.toExpectedFact(),
            ),
            facts,
        )
    }

    private fun authority(
        requestId: UUID,
        assignedPartyId: UUID,
        delegatePrincipal: PrincipalRef,
        requirementId: UUID?,
        active: Boolean = true,
        effectiveAt: Timestamp = Timestamp.from(Instant.now().minusSeconds(60)),
        expiresAt: Timestamp? = null,
    ): InformationRequestDelegatedAuthority =
        InformationRequestDelegatedAuthority().apply {
            this.informationRequestId = requestId
            this.assignedPartyId = assignedPartyId
            delegatePrincipalKind = delegatePrincipal.kind
            delegatePrincipalId = delegatePrincipal.id
            this.requirementId = requirementId
            this.active = active
            this.effectiveAt = effectiveAt
            this.expiresAt = expiresAt
        }

    private fun InformationRequestDelegatedAuthority.toExpectedFact() =
        InformationRequestRequirementDelegatedAuthorityFact(
            authorityId = id,
            assignedPartyId = assignedPartyId,
            delegatePrincipal = PrincipalRef(delegatePrincipalKind, delegatePrincipalId),
            requestId = informationRequestId,
            requirementId = requirementId,
            active = active,
        )
}
