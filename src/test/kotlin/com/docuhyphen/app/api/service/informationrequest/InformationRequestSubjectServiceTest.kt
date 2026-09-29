package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.SubjectIdentityExternalIdentifier
import com.docuhyphen.app.api.model.entity.SubjectIdentityOwnerType
import com.docuhyphen.app.api.model.entity.SubjectIdentityRef
import com.docuhyphen.app.api.model.entity.SubjectKind
import com.docuhyphen.app.api.model.informationrequest.AssignInformationRequestSubjectCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubjectReference
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.SubjectIdentityExternalIdentifierRepository
import com.docuhyphen.app.api.repository.informationrequest.SubjectIdentityRefRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestSubjectServiceTest
{
    private val organizationId = UUID.randomUUID()
    private val actor = PrincipalRef.user(UUID.randomUUID())
    private val access = RequestAccessContext(actor, AuthorizationContext(activeOrgId = organizationId))
    private val request = InformationRequest().apply {
        id = UUID.randomUUID()
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = organizationId
    }
    private val reference = InformationRequestSubjectReference("Records office", "Account", "A-100")
    private val requestRepository = mock<InformationRequestRepository>()
    private val subjectRepository = mock<SubjectIdentityRefRepository>()
    private val identifierRepository = mock<SubjectIdentityExternalIdentifierRepository>()
    private val partyService = mock<InformationRequestPartyService>()
    private val authorizationService = mock<AuthorizationService>()
    private val ownerScopeAccess = mock<InformationRequestOwnerScopeAccess>()
    private val saved = mutableListOf<SubjectIdentityRef>()
    private val service = InformationRequestSubjectService(
        requestRepository = requestRepository,
        subjectRepository = subjectRepository,
        identifierRepository = identifierRepository,
        partyService = partyService,
        authorizationService = authorizationService,
        ownerScopeAccess = ownerScopeAccess,
    )

    init
    {
        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(
            authorizationService.authorize(
                actor,
                Action.INFORMATION_REQUEST_MANAGE_PARTIES,
                ResourceRef.informationRequest(request.id),
                access.authorization,
            ),
        ).thenReturn(Decision.Allow())
        whenever(subjectRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<SubjectIdentityRef>(0).also { subject -> saved += subject }
        }
        whenever(subjectRepository.findById(any())).thenAnswer { invocation ->
            saved.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
        }
        whenever(partyService.assign(any())).thenAnswer {
            InformationRequestPartyAssignmentResult(
                party = InformationRequestParty().apply {
                    informationRequestId = request.id
                    roleKey = InformationRequestShareRoleKey.SUBJECT
                },
                partiesETag = "\"parties-2\"",
                partyETag = "\"party-1\"",
            )
        }
    }

    private fun command(key: String = "assign-subject", withReference: Boolean = true) = AssignInformationRequestSubjectCommand(
        requestId = request.id,
        subjectKind = SubjectKind.RECORD,
        reference = reference.takeIf { withReference },
        access = access,
        precondition = CommandPrecondition.ExpectedRevision(setOf("\"parties-1\"")),
        idempotencyKey = key,
    )

    @Test
    fun `a new subject is created in the request owner's scope with its reference and assigned as the subject party`()
    {
        service.assignSubject(command())

        val subject = saved.single()
        assertEquals(SubjectIdentityOwnerType.ORGANIZATION, subject.ownerType)
        assertEquals(organizationId, subject.ownerOrganizationId)
        assertEquals(SubjectKind.RECORD, subject.subjectKind)
        val identifier = argumentCaptor<SubjectIdentityExternalIdentifier>()
        verify(identifierRepository).save(identifier.capture())
        assertEquals(subject.id, identifier.firstValue.subjectIdentityRefId)
        assertEquals("A-100", identifier.firstValue.identifierValue)
        assertEquals(actor.id, identifier.firstValue.authorizedByPrincipalId)
        val assigned = argumentCaptor<AssignInformationRequestPartyCommand>()
        verify(partyService).assign(assigned.capture())
        assertEquals(InformationRequestShareRoleKey.SUBJECT, assigned.firstValue.roleKey)
        assertEquals(subject.id, assigned.firstValue.subjectIdentityRefId)
        assertEquals("assign-subject", assigned.firstValue.idempotencyKey)
    }

    @Test
    fun `a reference the owner already knows names the same subject`()
    {
        val known = UUID.randomUUID()
        whenever(identifierRepository.findByTenantValue(SubjectIdentityOwnerType.ORGANIZATION, organizationId, "Records office", "Account", "A-100"))
            .thenReturn(SubjectIdentityExternalIdentifier().apply { subjectIdentityRefId = known })

        service.assignSubject(command())

        verify(subjectRepository, never()).save(any())
        val assigned = argumentCaptor<AssignInformationRequestPartyCommand>()
        verify(partyService).assign(assigned.capture())
        assertEquals(known, assigned.firstValue.subjectIdentityRefId)
    }

    @Test
    fun `retrying the same command without a reference creates the subject once`()
    {
        service.assignSubject(command(withReference = false))
        service.assignSubject(command(withReference = false))

        assertEquals(1, saved.size)
        val assigned = argumentCaptor<AssignInformationRequestPartyCommand>()
        verify(partyService, org.mockito.kotlin.times(2)).assign(assigned.capture())
        assertEquals(assigned.firstValue.subjectIdentityRefId, assigned.secondValue.subjectIdentityRefId)
    }

    @Test
    fun `a caller who may not manage parties creates no subject`()
    {
        whenever(
            authorizationService.authorize(
                actor,
                Action.INFORMATION_REQUEST_MANAGE_PARTIES,
                ResourceRef.informationRequest(request.id),
                access.authorization,
            ),
        ).thenReturn(Decision.Deny("DENIED", "denied"))

        assertThrows<ForbiddenException> { service.assignSubject(command()) }

        verify(subjectRepository, never()).save(any())
        verify(partyService, never()).assign(any())
    }

    @Test
    fun `the owner's subjects are listed with their references for a privacy manager`()
    {
        val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, organizationId)
        val subject = SubjectIdentityRef().apply {
            ownerType = SubjectIdentityOwnerType.ORGANIZATION
            ownerOrganizationId = organizationId
            subjectKind = SubjectKind.PERSON
        }
        whenever(ownerScopeAccess.currentOwner()).thenReturn(owner)
        whenever(ownerScopeAccess.requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)).thenReturn(actor)
        whenever(subjectRepository.findForOwner(SubjectIdentityOwnerType.ORGANIZATION, organizationId)).thenReturn(listOf(subject))
        whenever(identifierRepository.findForSubjects(listOf(subject.id))).thenReturn(
            listOf(SubjectIdentityExternalIdentifier().apply {
                subjectIdentityRefId = subject.id
                authority = "Records office"
                identifierType = "Account"
                identifierValue = "A-200"
            }),
        )

        val listed = service.listForOwner()

        assertEquals(subject.id, listed.single().id)
        assertEquals(SubjectKind.PERSON, listed.single().subjectKind)
        assertEquals("A-200", listed.single().references.single().identifierValue)
        verify(ownerScopeAccess).requireAccess(owner, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
    }
}
