package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestCorrection
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNextAction
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSummaryPermissions
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestCallerStandingServiceTest
{
    private val caller = PrincipalRef.user(UUID.randomUUID())
    private val access = RequestAccessContext(caller, AuthorizationContext())
    private val partyRepository = mock<InformationRequestPartyRepository>()
    private val requirementContext = mock<InformationRequestRequirementAuthorizationContextProvider>()
    private val gate = mock<InformationRequestMutationGate>()
    private val lockService = mock<InformationRequestSubmissionLockService>()
    private val stages = mock<InformationRequestSubmissionStages>()
    private val service = InformationRequestCallerStandingService(partyRepository, requirementContext, gate, lockService, stages)
    private val request = InformationRequest().apply { state = InformationRequestState.ISSUED }

    init
    {
        whenever(gate.permitsRequest(any(), any(), any())).thenReturn(false)
        whenever(partyRepository.findActiveForRequest(request.id)).thenReturn(emptyList())
        whenever(stages.stageOrder(request)).thenReturn(emptyList())
        whenever(lockService.submittedStages(request.id)).thenReturn(emptySet())
        whenever(lockService.openCorrections(request.id)).thenReturn(emptyList())
    }

    @Test
    fun `a responding party on open work that is not submitted is asked to respond`()
    {
        actingParty(InformationRequestShareRoleKey.CONTRIBUTOR)
        permits(Action.INFORMATION_REQUEST_SUBMIT)

        val standing = service.standingOf(request, access, emptySet())

        assertEquals(listOf(InformationRequestShareRoleKey.CONTRIBUTOR), standing.roles)
        assertEquals(InformationRequestSummaryPermissions(canManage = false, canRespond = true, canReview = false), standing.permissions)
        assertEquals(InformationRequestNextAction.RESPOND, standing.nextAction)
    }

    @Test
    fun `a submitted package waits for review until a correction or an unsubmitted stage reopens it`()
    {
        actingParty(InformationRequestShareRoleKey.PREPARER)
        permits(Action.INFORMATION_REQUEST_SUBMIT)
        whenever(lockService.submittedStages(request.id)).thenReturn(setOf(null))

        val submitted = service.standingOf(request, access, emptySet()).nextAction
        whenever(lockService.openCorrections(request.id)).thenReturn(listOf(InformationRequestCorrection()))
        val corrected = service.standingOf(request, access, emptySet()).nextAction
        whenever(lockService.openCorrections(request.id)).thenReturn(emptyList())
        whenever(stages.stageOrder(request)).thenReturn(listOf("first", "second"))
        whenever(lockService.submittedStages(request.id)).thenReturn(setOf("first"))
        val staged = service.standingOf(request, access, emptySet()).nextAction
        whenever(lockService.submittedStages(request.id)).thenReturn(setOf("first", "second"))
        val allStages = service.standingOf(request, access, emptySet()).nextAction

        assertEquals(InformationRequestNextAction.VIEW, submitted)
        assertEquals(InformationRequestNextAction.RESPOND, corrected)
        assertEquals(InformationRequestNextAction.RESPOND, staged)
        assertEquals(InformationRequestNextAction.VIEW, allStages)
    }

    @Test
    fun `an attesting party may respond although it cannot submit`()
    {
        actingParty(InformationRequestShareRoleKey.ATTESTOR)

        val standing = service.standingOf(request, access, emptySet())

        assertEquals(true, standing.permissions.canRespond)
        assertEquals(InformationRequestNextAction.RESPOND, standing.nextAction)
    }

    @Test
    fun `an assigned reviewer reviews, a manager sets up drafts and manages open work, and finished work is viewed`()
    {
        permits(Action.INFORMATION_REQUEST_REVIEW)

        val awaited = service.standingOf(request, access, setOf(request.id))
        val notAwaited = service.standingOf(request, access, emptySet())
        permits(Action.INFORMATION_REQUEST_MANAGE_PARTIES)
        val managed = service.standingOf(request, access, emptySet())
        request.state = InformationRequestState.DRAFT
        val draft = service.standingOf(request, access, emptySet())
        request.state = InformationRequestState.CLOSED
        val closed = service.standingOf(request, access, setOf(request.id))

        assertEquals(InformationRequestNextAction.REVIEW, awaited.nextAction)
        assertEquals(InformationRequestNextAction.VIEW, notAwaited.nextAction)
        assertEquals(InformationRequestNextAction.MANAGE, managed.nextAction)
        assertEquals(true, managed.permissions.canManage)
        assertEquals(InformationRequestNextAction.COMPLETE_SETUP, draft.nextAction)
        assertEquals(InformationRequestNextAction.VIEW, closed.nextAction)
    }

    @Test
    fun `the caller's roles include parties it acts for through a group and never another principal's`()
    {
        val groupId = UUID.randomUUID()
        val groupParty = InformationRequestParty().apply {
            informationRequestId = request.id
            roleKey = InformationRequestShareRoleKey.REVIEWER
            principalKind = PrincipalKind.PRINCIPAL_GROUP
            principalId = groupId
        }
        val otherParty = InformationRequestParty().apply {
            informationRequestId = request.id
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
            principalKind = PrincipalKind.USER
            principalId = UUID.randomUUID()
        }
        whenever(partyRepository.findActiveForRequest(request.id)).thenReturn(listOf(otherParty, groupParty))
        whenever(requirementContext.principalsActingFor(groupParty)).thenReturn(setOf(PrincipalRef.group(groupId), caller))
        whenever(requirementContext.principalsActingFor(otherParty)).thenReturn(setOf(PrincipalRef.user(otherParty.principalId!!)))

        val standing = service.standingOf(request, access, emptySet())

        assertEquals(listOf(InformationRequestShareRoleKey.REVIEWER), standing.roles)
        assertEquals(false, standing.permissions.canRespond)
    }

    private fun actingParty(role: InformationRequestShareRoleKey)
    {
        val party = InformationRequestParty().apply {
            informationRequestId = request.id
            roleKey = role
            principalKind = caller.kind
            principalId = caller.id
        }
        whenever(partyRepository.findActiveForRequest(request.id)).thenReturn(listOf(party))
        whenever(requirementContext.principalsActingFor(party)).thenReturn(setOf(caller))
    }

    private fun permits(action: Action)
    {
        whenever(gate.permitsRequest(eq(access), eq(action), eq(request.id))).thenReturn(true)
    }
}
