package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFact
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactRevocation
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactFreshness
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRevocationRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestSubjectRestrictionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class InformationRequestAcceptedFactEligibilityTest
{
    private val now = Instant.parse("2026-09-28T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val facts = mock<InformationRequestAcceptedFactRepository>()
    private val revocations = mock<InformationRequestAcceptedFactRevocationRepository>()
    private val evidenceReferences = mock<InformationRequestAcceptedFactEvidenceRepository>()
    private val standing = InformationRequestAcceptedFactStanding(facts, revocations, evidenceReferences, clock)
    private val requests = mock<InformationRequestQueryService>()
    private val gate = mock<InformationRequestMutationGate>()
    private val parties = mock<InformationRequestPartyRepository>()
    private val requirements = mock<InformationRequestRequirementRepository>()
    private val bindings = mock<InformationRequestTemplateRequirementBindingRepository>()
    private val templateRequirements = mock<InformationRequestTemplateRequirementRepository>()
    private val versions = mock<InformationRequestTemplateVersionRepository>()
    private val restrictions = mock<InformationRequestSubjectRestrictionService>()
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val subjectId = UUID.randomUUID()
    private val fieldId = UUID.randomUUID()
    private val request = InformationRequest().apply {
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
    }
    private val requirement = InformationRequestRequirement().apply {
        informationRequestId = request.id
        sourceTemplateBindingId = UUID.randomUUID()
        sourceTemplateRequirementId = UUID.randomUUID()
    }
    private val query = InformationRequestAcceptedFactQueryService(
        requests, gate, standing, facts, parties, requirements, bindings, templateRequirements, versions, restrictions,
    )
    private val stored = mutableListOf<InformationRequestAcceptedFact>()

    init
    {
        whenever(requests.findById(request.id, access)).thenReturn(request)
        whenever(versions.findById(request.templateVersionId)).thenReturn(
            InformationRequestTemplateVersion().apply { factReusePurposeKey = "profile.reuse" },
        )
        whenever(parties.findActiveForRequestRole(request.id, InformationRequestShareRoleKey.SUBJECT)).thenReturn(
            listOf(InformationRequestParty().apply { subjectIdentityRefId = subjectId }),
        )
        whenever(requirements.findForRequest(request.id)).thenReturn(listOf(requirement))
        whenever(bindings.findById(requirement.sourceTemplateBindingId)).thenReturn(
            InformationRequestTemplateRequirementBinding().apply { collectedFieldDefinitionId = fieldId },
        )
        whenever(templateRequirements.findById(requirement.sourceTemplateRequirementId)).thenReturn(
            InformationRequestTemplateRequirement().apply { requirementKey = "recorded-answer" },
        )
        whenever(gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, requirement.id)).thenReturn(true)
        whenever(facts.findForKey(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            stored.filter {
                it.ownerType == invocation.getArgument<InformationRequestOwnerType>(0) &&
                    (it.ownerOrganizationId ?: it.ownerUserId) == invocation.getArgument<UUID>(1) &&
                    it.subjectIdentityRefId == invocation.getArgument<UUID>(2) &&
                    it.fieldDefinitionId in invocation.getArgument<Collection<UUID>>(3) &&
                    it.purposeKey == invocation.getArgument<String>(4)
            }
        }
    }

    @Test
    fun `expiry and validity ends are exclusive while validity start is inclusive`()
    {
        val fact = fact()
        fact.expiresAt = Timestamp.from(now)
        assertEquals(InformationRequestAcceptedFactFreshness.EXPIRED, standing.view(fact).freshness)
        fact.expiresAt = null
        fact.validTo = Timestamp.from(now)
        assertEquals(InformationRequestAcceptedFactFreshness.OUTSIDE_VALID_PERIOD, standing.view(fact).freshness)
        fact.validTo = Timestamp.from(now.plusSeconds(1))
        fact.validFrom = Timestamp.from(now)
        assertEquals(InformationRequestAcceptedFactFreshness.CURRENT, standing.view(fact).freshness)
    }

    @Test
    fun `promoted evidence versions remain attached to the exact fact view`()
    {
        val fact = fact()
        val versionId = UUID.randomUUID()
        whenever(evidenceReferences.findForFacts(any())).thenReturn(
            listOf(InformationRequestAcceptedFactEvidence().apply {
                factId = fact.id
                sourceSubmissionEvidenceId = UUID.randomUUID()
                evidenceVersionId = versionId
            }),
        )
        assertEquals(listOf(versionId), standing.view(fact).evidenceVersionIds)
    }

    @Test
    fun `expired and future facts stay in history but cannot be offered`()
    {
        fact().expiresAt = Timestamp.from(now.minusSeconds(1))
        fact().validFrom = Timestamp.from(now.plusSeconds(1))
        fact().validTo = Timestamp.from(now.minusSeconds(1))
        assertEquals(3, query.promotedFrom(request.id, access).size)
        assertTrue(query.offers(request.id, access).isEmpty())
    }

    @Test
    fun `an expired newer promotion does not hide a current reusable fact`()
    {
        val current = fact()
        fact().apply {
            promotedAt = Timestamp.from(now)
            expiresAt = Timestamp.from(now.minusSeconds(1))
        }
        assertEquals(current.id, query.offers(request.id, access).single().fact.fact.id)
    }

    @Test
    fun `an expired superseding fact cannot resurrect the superseded fact`()
    {
        val prior = fact()
        val successor = fact().apply {
            supersedesFactId = prior.id
            expiresAt = Timestamp.from(now.minusSeconds(1))
        }
        whenever(facts.findSuperseding(any())).thenReturn(listOf(successor))
        assertTrue(query.offers(request.id, access).isEmpty())
    }

    @Test
    fun `only the target owner subject field and purpose are queried for either owner type`()
    {
        for (ownerType in InformationRequestOwnerType.entries)
        {
            stored.clear()
            request.ownerType = ownerType
            request.ownerOrganizationId = if (ownerType == InformationRequestOwnerType.ORGANIZATION) UUID.randomUUID() else null
            request.ownerUserId = if (ownerType == InformationRequestOwnerType.USER) UUID.randomUUID() else null
            val matching = fact()
            fact().subjectIdentityRefId = UUID.randomUUID()
            fact().fieldDefinitionId = UUID.randomUUID()
            fact().purposeKey = "profile.other"
            fact().apply {
                ownerOrganizationId = if (ownerType == InformationRequestOwnerType.ORGANIZATION) UUID.randomUUID() else null
                ownerUserId = if (ownerType == InformationRequestOwnerType.USER) UUID.randomUUID() else null
            }
            val offer = query.offers(request.id, access).single()
            assertEquals(matching.id, offer.fact.fact.id)
            assertEquals(requirement.id, offer.requirementId)
            assertTrue(offer.reconfirmationRequired)
        }
    }

    @Test
    fun `private revoked same-request and denied requirement facts are not offered`()
    {
        fact().visibility = InformationRequestAcceptedFactVisibility.REQUESTING_SIDE
        fact().sourceInformationRequestId = request.id
        val revoked = fact()
        whenever(revocations.findForFacts(any())).thenReturn(
            listOf(InformationRequestAcceptedFactRevocation().apply { factId = revoked.id }),
        )
        assertTrue(query.offers(request.id, access).isEmpty())
        fact()
        whenever(gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, requirement.id)).thenReturn(false)
        assertTrue(query.offers(request.id, access).isEmpty())
    }

    @Test
    fun `restricted subjects have no reusable offers`()
    {
        fact()
        whenever(restrictions.isRestricted(request.ownerType, requireNotNull(request.ownerOrganizationId), subjectId)).thenReturn(true)
        assertTrue(query.offers(request.id, access).isEmpty())
    }

    private fun fact(): InformationRequestAcceptedFact = InformationRequestAcceptedFact().apply {
        ownerType = request.ownerType
        ownerOrganizationId = request.ownerOrganizationId
        ownerUserId = request.ownerUserId
        subjectIdentityRefId = subjectId
        fieldDefinitionId = fieldId
        purposeKey = "profile.reuse"
        sourceInformationRequestId = UUID.randomUUID()
        visibility = InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES
        validFrom = Timestamp.from(now.minusSeconds(60))
        promotedAt = Timestamp.from(now.minusSeconds(60))
        stored.add(this)
        whenever(facts.findFromRequest(request.id)).thenAnswer { stored.toList() }
    }
}
