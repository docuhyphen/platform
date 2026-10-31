package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestFieldAudienceGuardTest {
    private val bindings = mock<InformationRequestTemplateRequirementBindingRepository>()
    private val templateRequirements = mock<InformationRequestTemplateRequirementRepository>()
    private val versions = mock<InformationRequestTemplateVersionRepository>()
    private val parties = mock<InformationRequestPartyRepository>()
    private val fields = mock<FieldsReadFacade>()
    private val memberships = mock<OrganizationMembershipService>()
    private val groups = mock<PrincipalGroupService>()
    private val guard = InformationRequestFieldAudienceGuard(
        bindings, templateRequirements, versions, parties, fields, memberships, groups,
    )

    private val organizationId = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val request = InformationRequest().apply {
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = organizationId
    }

    @Test
    fun `an outside participant cannot hold the role that answers an Internal Field, and the refusal names the Requirement`() {
        collects(request.templateVersionId, FieldDataClassification.INTERNAL)

        val refusal = assertThrows<InformationRequestLifecycleException> {
            guard.requireAnswerableBy(
                request,
                InformationRequestShareRoleKey.CONTRIBUTOR,
                PrincipalRef.participant(UUID.randomUUID())
            )
        }

        assertEquals(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, refusal.reasonCode)
        assertTrue(refusal.message.contains(REQUIREMENT_KEY)) { refusal.message }
        assertTrue(refusal.message.contains("contributor")) { refusal.message }
    }

    @Test
    fun `every classification other than Public is closed to an outside party`() {
        listOf(FieldDataClassification.CONFIDENTIAL, FieldDataClassification.RESTRICTED).forEach { classification ->
            val version = UUID.randomUUID()
            collects(version, classification)

            assertThrows<InformationRequestLifecycleException> {
                guard.requireAnswerableBy(
                    request.apply { templateVersionId = version },
                    InformationRequestShareRoleKey.CONTRIBUTOR,
                    PrincipalRef.participant(UUID.randomUUID()),
                )
            }
        }
    }

    @Test
    fun `a Public Field may be answered by an outside participant`() {
        collects(request.templateVersionId, FieldDataClassification.PUBLIC)

        assertDoesNotThrow {
            guard.requireAnswerableBy(
                request,
                InformationRequestShareRoleKey.CONTRIBUTOR,
                PrincipalRef.participant(UUID.randomUUID())
            )
        }
    }

    @Test
    fun `a member of the owning organization may answer an Internal Field and a person outside it may not`() {
        collects(request.templateVersionId, FieldDataClassification.INTERNAL)
        whenever(memberships.isMember(member, organizationId)).thenReturn(true)

        assertDoesNotThrow {
            guard.requireAnswerableBy(request, InformationRequestShareRoleKey.CONTRIBUTOR, PrincipalRef.user(member))
        }
        assertThrows<InformationRequestLifecycleException> {
            guard.requireAnswerableBy(
                request,
                InformationRequestShareRoleKey.CONTRIBUTOR,
                PrincipalRef.user(UUID.randomUUID())
            )
        }
    }

    @Test
    fun `on a personally owned request only the owner counts as inside`() {
        val owner = UUID.randomUUID()
        request.ownerType = InformationRequestOwnerType.USER
        request.ownerOrganizationId = null
        request.ownerUserId = owner
        collects(request.templateVersionId, FieldDataClassification.INTERNAL)

        assertDoesNotThrow {
            guard.requireAnswerableBy(request, InformationRequestShareRoleKey.CONTRIBUTOR, PrincipalRef.user(owner))
        }
        assertThrows<InformationRequestLifecycleException> {
            guard.requireAnswerableBy(
                request,
                InformationRequestShareRoleKey.CONTRIBUTOR,
                PrincipalRef.user(UUID.randomUUID())
            )
        }
    }

    @Test
    fun `a group counts as outside when any of its active members is outside the owner`() {
        collects(request.templateVersionId, FieldDataClassification.INTERNAL)
        whenever(memberships.isMember(member, organizationId)).thenReturn(true)
        val inside = UUID.randomUUID()
        val mixed = UUID.randomUUID()
        whenever(groups.activeMembers(inside)).thenReturn(listOf(groupMember(inside, PrincipalKind.USER, member)))
        whenever(groups.activeMembers(mixed)).thenReturn(
            listOf(
                groupMember(mixed, PrincipalKind.USER, member),
                groupMember(mixed, PrincipalKind.PARTICIPANT, UUID.randomUUID()),
            ),
        )

        assertDoesNotThrow {
            guard.requireAnswerableBy(request, InformationRequestShareRoleKey.CONTRIBUTOR, PrincipalRef.group(inside))
        }
        assertThrows<InformationRequestLifecycleException> {
            guard.requireAnswerableBy(request, InformationRequestShareRoleKey.CONTRIBUTOR, PrincipalRef.group(mixed))
        }
    }

    @Test
    fun `a Requirement its party only views places no limit on who holds the role`() {
        collects(
            request.templateVersionId,
            FieldDataClassification.INTERNAL,
            mode = InformationRequestResponseMode.VIEW_ONLY
        )

        assertDoesNotThrow {
            guard.requireAnswerableBy(
                request,
                InformationRequestShareRoleKey.CONTRIBUTOR,
                PrincipalRef.participant(UUID.randomUUID())
            )
        }
    }

    @Test
    fun `a role that does not answer the Internal Field may be held by an outside party`() {
        collects(
            request.templateVersionId,
            FieldDataClassification.INTERNAL,
            role = InformationRequestContributorRole.PREPARER
        )

        assertDoesNotThrow {
            guard.requireAnswerableBy(
                request,
                InformationRequestShareRoleKey.CONTRIBUTOR,
                PrincipalRef.participant(UUID.randomUUID())
            )
        }
        assertThrows<InformationRequestLifecycleException> {
            guard.requireAnswerableBy(
                request,
                InformationRequestShareRoleKey.PREPARER,
                PrincipalRef.participant(UUID.randomUUID())
            )
        }
    }

    @Test
    fun `issuing checks every active acting party and passes over a subject`() {
        collects(request.templateVersionId, FieldDataClassification.INTERNAL)
        whenever(memberships.isMember(member, organizationId)).thenReturn(true)
        val subject = party(InformationRequestShareRoleKey.SUBJECT, null)
        val insider = party(InformationRequestShareRoleKey.CONTRIBUTOR, PrincipalRef.user(member))
        val outsider = party(InformationRequestShareRoleKey.CONTRIBUTOR, PrincipalRef.participant(UUID.randomUUID()))
        whenever(parties.findActiveForRequest(request.id)).thenReturn(listOf(subject, insider))

        assertDoesNotThrow { guard.requireAnswerableByParties(request) }

        whenever(parties.findActiveForRequest(request.id)).thenReturn(listOf(subject, insider, outsider))
        val refusal = assertThrows<InformationRequestLifecycleException> { guard.requireAnswerableByParties(request) }
        assertEquals(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, refusal.reasonCode)
    }

    @Test
    fun `an amendment target is judged by the Fields of its own Schema`() {
        val target = UUID.randomUUID()
        collects(request.templateVersionId, FieldDataClassification.PUBLIC)
        collects(target, FieldDataClassification.INTERNAL)
        whenever(parties.findActiveForRequest(request.id)).thenReturn(
            listOf(party(InformationRequestShareRoleKey.CONTRIBUTOR, PrincipalRef.participant(UUID.randomUUID()))),
        )

        assertDoesNotThrow { guard.requireAnswerableByParties(request) }
        assertThrows<InformationRequestLifecycleException> { guard.requireAnswerableByParties(request, target) }
    }

    @Test
    fun `a version that collects no Field reads no parties`() {
        whenever(bindings.findOrdered(request.templateVersionId)).thenReturn(listOf(binding(null)))

        guard.requireAnswerableByParties(request)

        verify(parties, never()).findActiveForRequest(any())
    }

    private fun collects(
        templateVersionId: UUID,
        classification: FieldDataClassification,
        mode: InformationRequestResponseMode = InformationRequestResponseMode.PROVIDE,
        role: InformationRequestContributorRole = InformationRequestContributorRole.CONTRIBUTOR,
    ) {
        val fieldDefinitionId = UUID.randomUUID()
        val schemaVersionId = UUID.randomUUID()
        val requirement = InformationRequestTemplateRequirement().apply {
            templateDefinitionId = UUID.randomUUID()
            requirementKey = REQUIREMENT_KEY
            requirementType = InformationRequestRequirementType.FIELD
        }
        val binding = binding(fieldDefinitionId).apply {
            this.templateVersionId = templateVersionId
            templateRequirementId = requirement.id
            responseMode = mode
            contributorRole = role
        }
        whenever(bindings.findOrdered(templateVersionId)).thenReturn(listOf(binding))
        whenever(templateRequirements.findById(requirement.id)).thenReturn(requirement)
        whenever(versions.findById(templateVersionId)).thenReturn(
            InformationRequestTemplateVersion().apply {
                id = templateVersionId
                templateDefinitionId = requirement.templateDefinitionId
                this.schemaVersionId = schemaVersionId
            },
        )
        whenever(fields.schemaBindings(schemaVersionId)).thenReturn(
            listOf(
                SchemaFieldBinding().apply {
                    this.schemaVersionId = schemaVersionId
                    fieldContractId = UUID.randomUUID()
                    this.fieldDefinitionId = fieldDefinitionId
                    visibility = classification
                },
            ),
        )
    }

    private fun binding(fieldDefinitionId: UUID?): InformationRequestTemplateRequirementBinding =
        InformationRequestTemplateRequirementBinding().apply {
            templateVersionId = request.templateVersionId
            templateDefinitionId = UUID.randomUUID()
            templateRequirementId = UUID.randomUUID()
            templateSectionId = UUID.randomUUID()
            prompt = "Record the note"
            collectedFieldDefinitionId = fieldDefinitionId
        }

    private fun party(roleKey: InformationRequestShareRoleKey, principal: PrincipalRef?): InformationRequestParty =
        InformationRequestParty().apply {
            informationRequestId = request.id
            this.roleKey = roleKey
            principalKind = principal?.kind
            principalId = principal?.id
            subjectIdentityRefId = if (principal == null) UUID.randomUUID() else null
        }

    private fun groupMember(groupId: UUID, kind: PrincipalKind, principalId: UUID): PrincipalGroupMember =
        PrincipalGroupMember().apply {
            principalGroupId = groupId
            principalKind = kind
            this.principalId = principalId
        }

    private companion object {
        const val REQUIREMENT_KEY = "recorded-note"
    }
}
