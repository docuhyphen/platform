package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.fields.FieldBindingAccess
import com.docuhyphen.app.api.service.fields.FieldBindingDecision
import com.docuhyphen.app.api.service.fields.FieldBindingDenial
import com.docuhyphen.app.api.service.fields.FieldValueOperation
import com.docuhyphen.app.api.service.fields.FieldValueSetRef
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestFieldBindingPolicyTest
{
    private val requestId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val templateBindingId = UUID.randomUUID()
    private val fieldDefinitionId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val access = FieldsAccessContext(PrincipalRef.user(actorId), AuthorizationContext())
    private val resource = FieldsResourceRef("INFORMATION_REQUEST", requestId)

    private fun internalRequestRepository(): InformationRequestRepository
    {
        val repository = mock<InformationRequestRepository>()
        whenever(repository.findById(requestId)).thenReturn(
            InformationRequest().apply {
                id = requestId
                exchangeId = UUID.randomUUID()
                templateVersionId = UUID.randomUUID()
                ownerType = InformationRequestOwnerType.ORGANIZATION
                ownerOrganizationId = organizationId
            },
        )
        return repository
    }

    private fun internalMembershipRepository(): OrganizationMembershipRepository
    {
        val repository = mock<OrganizationMembershipRepository>()
        whenever(repository.findActiveByUserAndOrg(actorId, organizationId)).thenReturn(mock())
        return repository
    }

    @Test
    fun `a binding answered against a Requirement is authorized against that Requirement occurrence`()
    {
        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                ResourceRef.informationRequestRequirement(requirementId),
                access.authorization,
            ),
        ).thenReturn(
            Decision.Deny(
                Decision.REASON_NO_GRANT,
                "Principal is not the assigned party for this Requirement",
            ),
        )
        val policy = policy(authorizationService)

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = access,
                valueSet = FieldValueSetRef.Root,
                binding = binding(),
                operation = FieldValueOperation.WRITE,
            ),
        )

        assertEquals(FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE), decision)
        verify(authorizationService).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        )
    }

    @Test
    fun `a binding answered against a Requirement is permitted once the caller is authorized for it`()
    {
        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                ResourceRef.informationRequestRequirement(requirementId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        val policy = policy(authorizationService)

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = access,
                valueSet = FieldValueSetRef.Root,
                binding = binding(),
                operation = FieldValueOperation.READ,
            ),
        )

        assertTrue(decision is FieldBindingDecision.Allow)
    }

    @Test
    fun `a binding with no answering Requirement is left to the shared audience rule without consulting central authorization`()
    {
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(emptyList())
        val authorizationService = mock<AuthorizationService>()
        val policy = InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = mock(),
            authorizationService = authorizationService,
            requestRepository = internalRequestRepository(),
            organizationMembershipRepository = internalMembershipRepository(),
        )

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = access,
                valueSet = FieldValueSetRef.Root,
                binding = binding(),
                operation = FieldValueOperation.READ,
            ),
        )

        assertTrue(decision is FieldBindingDecision.Allow)
        verify(authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `an unregistered respondent is denied an uncollected INTERNAL Schema Field even though no Requirement excludes it`()
    {
        val participantAccess = FieldsAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext())
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(emptyList())
        val authorizationService = mock<AuthorizationService>()
        val policy = InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = mock(),
            authorizationService = authorizationService,
            requestRepository = internalRequestRepository(),
            organizationMembershipRepository = internalMembershipRepository(),
        )

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = participantAccess,
                valueSet = FieldValueSetRef.Root,
                binding = binding(),
                operation = FieldValueOperation.READ,
            ),
        )

        assertEquals(FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE), decision)
        verify(authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `a registered respondent outside the owning organization is denied an uncollected CONFIDENTIAL Schema Field and cannot write it`()
    {
        val respondentId = UUID.randomUUID()
        val respondentAccess = FieldsAccessContext(PrincipalRef.user(respondentId), AuthorizationContext())
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(emptyList())
        val requestRepository = mock<InformationRequestRepository>()
        whenever(requestRepository.findById(requestId)).thenReturn(
            InformationRequest().apply {
                id = requestId
                exchangeId = UUID.randomUUID()
                templateVersionId = UUID.randomUUID()
                ownerType = InformationRequestOwnerType.ORGANIZATION
                ownerOrganizationId = organizationId
            },
        )
        val organizationMembershipRepository = mock<OrganizationMembershipRepository>()
        whenever(organizationMembershipRepository.findActiveByUserAndOrg(respondentId, organizationId)).thenReturn(null)
        val authorizationService = mock<AuthorizationService>()
        val policy = InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = mock(),
            authorizationService = authorizationService,
            requestRepository = requestRepository,
            organizationMembershipRepository = organizationMembershipRepository,
        )
        val confidentialBinding = binding().apply { visibility = FieldDataClassification.CONFIDENTIAL }

        val readDecision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = respondentAccess,
                valueSet = FieldValueSetRef.Root,
                binding = confidentialBinding,
                operation = FieldValueOperation.READ,
            ),
        )
        val writeDecision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = respondentAccess,
                valueSet = FieldValueSetRef.Root,
                binding = confidentialBinding,
                operation = FieldValueOperation.WRITE,
            ),
        )

        assertEquals(FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE), readDecision)
        assertEquals(FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE), writeDecision)
        verify(authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `an owner organization member retains access to an uncollected Schema Field`()
    {
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(emptyList())
        val authorizationService = mock<AuthorizationService>()
        val policy = InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = mock(),
            authorizationService = authorizationService,
            requestRepository = internalRequestRepository(),
            organizationMembershipRepository = internalMembershipRepository(),
        )

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = access,
                valueSet = FieldValueSetRef.Root,
                binding = binding(),
                operation = FieldValueOperation.READ,
            ),
        )

        assertTrue(decision is FieldBindingDecision.Allow)
        verify(authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `a read-only binding is still denied for writes without consulting central authorization`()
    {
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        val authorizationService = mock<AuthorizationService>()
        val policy = InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = mock(),
            authorizationService = authorizationService,
            requestRepository = internalRequestRepository(),
            organizationMembershipRepository = internalMembershipRepository(),
        )

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = access,
                valueSet = FieldValueSetRef.Root,
                binding = binding().apply { isReadOnly = true },
                operation = FieldValueOperation.WRITE,
            ),
        )

        assertEquals(FieldBindingDecision.Deny(FieldBindingDenial.READ_ONLY), decision)
        verify(requirementRepository, never()).findForRequest(any())
        verify(authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `a repeated Field write is authorized against the addressed occurrence, never a sibling occurrence's Requirement`()
    {
        val firstOccurrenceRequirementId = UUID.randomUUID()
        val secondOccurrenceRequirementId = UUID.randomUUID()
        val firstTemplateBindingId = UUID.randomUUID()
        val secondTemplateBindingId = UUID.randomUUID()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(
            listOf(
                InformationRequestRequirement().apply {
                    id = firstOccurrenceRequirementId
                    informationRequestId = requestId
                    sourceTemplateVersionId = UUID.randomUUID()
                    sourceTemplateRequirementId = UUID.randomUUID()
                    sourceTemplateBindingId = firstTemplateBindingId
                    occurrencePath = "items[0]"
                },
                InformationRequestRequirement().apply {
                    id = secondOccurrenceRequirementId
                    informationRequestId = requestId
                    sourceTemplateVersionId = UUID.randomUUID()
                    sourceTemplateRequirementId = UUID.randomUUID()
                    sourceTemplateBindingId = secondTemplateBindingId
                    occurrencePath = "items[1]"
                },
            ),
        )
        val templateBindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        listOf(firstTemplateBindingId, secondTemplateBindingId).forEach { templateBindingId ->
            whenever(templateBindingRepository.findById(templateBindingId)).thenReturn(
                InformationRequestTemplateRequirementBinding().apply {
                    id = templateBindingId
                    templateVersionId = UUID.randomUUID()
                    templateDefinitionId = UUID.randomUUID()
                    templateRequirementId = UUID.randomUUID()
                    templateSectionId = UUID.randomUUID()
                    prompt = "Prompt"
                    collectedFieldDefinitionId = fieldDefinitionId
                },
            )
        }
        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                ResourceRef.informationRequestRequirement(secondOccurrenceRequirementId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        val policy = InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = templateBindingRepository,
            authorizationService = authorizationService,
            requestRepository = internalRequestRepository(),
            organizationMembershipRepository = internalMembershipRepository(),
        )

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = access,
                valueSet = FieldValueSetRef.Occurrence("items[1]"),
                binding = binding(),
                operation = FieldValueOperation.WRITE,
            ),
        )

        assertTrue(decision is FieldBindingDecision.Allow)
        verify(authorizationService).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(secondOccurrenceRequirementId),
            access.authorization,
        )
        verify(authorizationService, never()).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(firstOccurrenceRequirementId),
            access.authorization,
        )
    }

    @Test
    fun `a delegate authorized only for one occurrence cannot have that authorization reused for a sibling occurrence's write`()
    {
        val firstOccurrenceRequirementId = UUID.randomUUID()
        val secondOccurrenceRequirementId = UUID.randomUUID()
        val firstTemplateBindingId = UUID.randomUUID()
        val secondTemplateBindingId = UUID.randomUUID()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(
            listOf(
                InformationRequestRequirement().apply {
                    id = firstOccurrenceRequirementId
                    informationRequestId = requestId
                    sourceTemplateVersionId = UUID.randomUUID()
                    sourceTemplateRequirementId = UUID.randomUUID()
                    sourceTemplateBindingId = firstTemplateBindingId
                    occurrencePath = "items[0]"
                },
                InformationRequestRequirement().apply {
                    id = secondOccurrenceRequirementId
                    informationRequestId = requestId
                    sourceTemplateVersionId = UUID.randomUUID()
                    sourceTemplateRequirementId = UUID.randomUUID()
                    sourceTemplateBindingId = secondTemplateBindingId
                    occurrencePath = "items[1]"
                },
            ),
        )
        val templateBindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        listOf(firstTemplateBindingId, secondTemplateBindingId).forEach { templateBindingId ->
            whenever(templateBindingRepository.findById(templateBindingId)).thenReturn(
                InformationRequestTemplateRequirementBinding().apply {
                    id = templateBindingId
                    templateVersionId = UUID.randomUUID()
                    templateDefinitionId = UUID.randomUUID()
                    templateRequirementId = UUID.randomUUID()
                    templateSectionId = UUID.randomUUID()
                    prompt = "Prompt"
                    collectedFieldDefinitionId = fieldDefinitionId
                },
            )
        }
        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                ResourceRef.informationRequestRequirement(firstOccurrenceRequirementId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                ResourceRef.informationRequestRequirement(secondOccurrenceRequirementId),
                access.authorization,
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "Principal is not the assigned party for this Requirement"))
        val policy = InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = templateBindingRepository,
            authorizationService = authorizationService,
            requestRepository = internalRequestRepository(),
            organizationMembershipRepository = internalMembershipRepository(),
        )

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = access,
                valueSet = FieldValueSetRef.Occurrence("items[1]"),
                binding = binding(),
                operation = FieldValueOperation.WRITE,
            ),
        )

        assertEquals(FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE), decision)
    }

    @Test
    fun `a repeated Field read is authorized against the addressed occurrence, never a sibling occurrence's Requirement`()
    {
        val firstOccurrenceRequirementId = UUID.randomUUID()
        val secondOccurrenceRequirementId = UUID.randomUUID()
        val firstTemplateBindingId = UUID.randomUUID()
        val secondTemplateBindingId = UUID.randomUUID()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(
            listOf(
                InformationRequestRequirement().apply {
                    id = firstOccurrenceRequirementId
                    informationRequestId = requestId
                    sourceTemplateVersionId = UUID.randomUUID()
                    sourceTemplateRequirementId = UUID.randomUUID()
                    sourceTemplateBindingId = firstTemplateBindingId
                    occurrencePath = "items[0]"
                },
                InformationRequestRequirement().apply {
                    id = secondOccurrenceRequirementId
                    informationRequestId = requestId
                    sourceTemplateVersionId = UUID.randomUUID()
                    sourceTemplateRequirementId = UUID.randomUUID()
                    sourceTemplateBindingId = secondTemplateBindingId
                    occurrencePath = "items[1]"
                },
            ),
        )
        val templateBindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        listOf(firstTemplateBindingId, secondTemplateBindingId).forEach { templateBindingId ->
            whenever(templateBindingRepository.findById(templateBindingId)).thenReturn(
                InformationRequestTemplateRequirementBinding().apply {
                    id = templateBindingId
                    templateVersionId = UUID.randomUUID()
                    templateDefinitionId = UUID.randomUUID()
                    templateRequirementId = UUID.randomUUID()
                    templateSectionId = UUID.randomUUID()
                    prompt = "Prompt"
                    collectedFieldDefinitionId = fieldDefinitionId
                },
            )
        }
        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                ResourceRef.informationRequestRequirement(secondOccurrenceRequirementId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        val policy = InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = templateBindingRepository,
            authorizationService = authorizationService,
            requestRepository = internalRequestRepository(),
            organizationMembershipRepository = internalMembershipRepository(),
        )

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = access,
                valueSet = FieldValueSetRef.Occurrence("items[1]"),
                binding = binding(),
                operation = FieldValueOperation.READ,
            ),
        )

        assertTrue(decision is FieldBindingDecision.Allow)
        verify(authorizationService).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            ResourceRef.informationRequestRequirement(secondOccurrenceRequirementId),
            access.authorization,
        )
        verify(authorizationService, never()).authorize(
            access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            ResourceRef.informationRequestRequirement(firstOccurrenceRequirementId),
            access.authorization,
        )
    }

    @Test
    fun `a delegate authorized only for one occurrence's read cannot have that authorization reused for a sibling occurrence's read`()
    {
        val firstOccurrenceRequirementId = UUID.randomUUID()
        val secondOccurrenceRequirementId = UUID.randomUUID()
        val firstTemplateBindingId = UUID.randomUUID()
        val secondTemplateBindingId = UUID.randomUUID()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(
            listOf(
                InformationRequestRequirement().apply {
                    id = firstOccurrenceRequirementId
                    informationRequestId = requestId
                    sourceTemplateVersionId = UUID.randomUUID()
                    sourceTemplateRequirementId = UUID.randomUUID()
                    sourceTemplateBindingId = firstTemplateBindingId
                    occurrencePath = "items[0]"
                },
                InformationRequestRequirement().apply {
                    id = secondOccurrenceRequirementId
                    informationRequestId = requestId
                    sourceTemplateVersionId = UUID.randomUUID()
                    sourceTemplateRequirementId = UUID.randomUUID()
                    sourceTemplateBindingId = secondTemplateBindingId
                    occurrencePath = "items[1]"
                },
            ),
        )
        val templateBindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        listOf(firstTemplateBindingId, secondTemplateBindingId).forEach { templateBindingId ->
            whenever(templateBindingRepository.findById(templateBindingId)).thenReturn(
                InformationRequestTemplateRequirementBinding().apply {
                    id = templateBindingId
                    templateVersionId = UUID.randomUUID()
                    templateDefinitionId = UUID.randomUUID()
                    templateRequirementId = UUID.randomUUID()
                    templateSectionId = UUID.randomUUID()
                    prompt = "Prompt"
                    collectedFieldDefinitionId = fieldDefinitionId
                },
            )
        }
        val authorizationService = mock<AuthorizationService>()
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                ResourceRef.informationRequestRequirement(firstOccurrenceRequirementId),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        whenever(
            authorizationService.authorize(
                access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                ResourceRef.informationRequestRequirement(secondOccurrenceRequirementId),
                access.authorization,
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "Principal is not the assigned party for this Requirement"))
        val policy = InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = templateBindingRepository,
            authorizationService = authorizationService,
            requestRepository = internalRequestRepository(),
            organizationMembershipRepository = internalMembershipRepository(),
        )

        val decision = policy.decide(
            FieldBindingAccess(
                resource = resource,
                access = access,
                valueSet = FieldValueSetRef.Occurrence("items[1]"),
                binding = binding(),
                operation = FieldValueOperation.READ,
            ),
        )

        assertEquals(FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE), decision)
    }

    private fun policy(authorizationService: AuthorizationService): InformationRequestFieldBindingPolicy
    {
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requirementRepository.findForRequest(requestId)).thenReturn(
            listOf(
                InformationRequestRequirement().apply {
                    id = requirementId
                    informationRequestId = requestId
                    sourceTemplateVersionId = UUID.randomUUID()
                    sourceTemplateRequirementId = UUID.randomUUID()
                    sourceTemplateBindingId = templateBindingId
                    occurrencePath = "root"
                },
            ),
        )
        val templateBindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        whenever(templateBindingRepository.findById(templateBindingId)).thenReturn(
            InformationRequestTemplateRequirementBinding().apply {
                id = templateBindingId
                templateVersionId = UUID.randomUUID()
                templateDefinitionId = UUID.randomUUID()
                templateRequirementId = UUID.randomUUID()
                templateSectionId = UUID.randomUUID()
                prompt = "Prompt"
                collectedFieldDefinitionId = fieldDefinitionId
            },
        )
        return InformationRequestFieldBindingPolicy(
            requirementRepository = requirementRepository,
            templateBindingRepository = templateBindingRepository,
            authorizationService = authorizationService,
            requestRepository = internalRequestRepository(),
            organizationMembershipRepository = internalMembershipRepository(),
        )
    }

    private fun binding() = SchemaFieldBinding().apply {
        schemaVersionId = UUID.randomUUID()
        fieldContractId = UUID.randomUUID()
        fieldDefinitionId = this@InformationRequestFieldBindingPolicyTest.fieldDefinitionId
        visibility = FieldDataClassification.INTERNAL
    }
}
