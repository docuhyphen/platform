package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.BlueprintInformationRequestPartyDefault
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.party.AssignExternalParticipantInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestPartyWritersTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["assign", "reassign"])
    fun `a suspended or unavailable trusted recipient is refused for new assignment and reassignment`(operation: String) {
        listOf(
            true to InformationRequestErrorCatalog.TRUST_SUSPENDED,
            false to InformationRequestErrorCatalog.TRUSTED_RECIPIENT_UNAVAILABLE
        )
            .forEach { (suspended, reasonCode) ->
                val fixture = InformationRequestPartyFixture()
                val recipient = fixture.exchangeRecipient()
                val holder = PrincipalRef.user(UUID.randomUUID())
                if (suspended) {
                    whenever(fixture.exchangeRecipientService.trustSuspended(recipient.id)).thenReturn(true)
                } else {
                    whenever(
                        fixture.exchangeRecipientService.requireAssignablePartyRecipient(
                            recipient.id,
                            fixture.request.exchangeId,
                            holder
                        )
                    )
                        .thenThrow(com.docuhyphen.app.api.exception.OrganizationTrustException("The trusted group is no longer published"))
                }
                val party = fixture.activeActingParty()

                val refusal =
                    assertThrows(InformationRequestLifecycleException::class.java) {
                        if (operation == "assign")
                            fixture.assignments.assign(
                                AssignInformationRequestPartyCommand(
                                    requestId = fixture.request.id,
                                    roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                                    principal = holder,
                                    exchangeRecipientId = recipient.id,
                                    access = RequestAccessContext(
                                        PrincipalRef.user(UUID.randomUUID()),
                                        fixture.authorizationContext
                                    ),
                                    precondition = CommandPrecondition.ExpectedRevision(
                                        InformationRequestETag.partiesOf(
                                            fixture.request
                                        )
                                    ),
                                    idempotencyKey = "assign-$reasonCode",
                                ),
                            )
                        else
                            fixture.revocations.reassign(
                                fixture.reassignCommand(
                                    party,
                                    holder,
                                    "reassign-$reasonCode",
                                    exchangeRecipientId = recipient.id
                                )
                            )
                    }

                assertEquals(reasonCode, refusal.reasonCode, "$operation $reasonCode")
                verify(fixture.shareService, never()).revoke(any(), anyOrNull(), anyOrNull())
                verify(fixture.partyRepository, never()).save(any())
            }
    }

    @Test
    fun `a new party answers to the continuation gate while a revocation does not`() {
        val fixture = InformationRequestPartyFixture()
        fixture.request.state = InformationRequestState.ISSUED
        val existingParty = fixture.activeActingParty()
        doThrow(
            SubscriptionDenialException(
                SubscriptionDenial(
                    reason = SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED,
                    planCode = PlanCode.BUSINESS,
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    message = "This subscription is suspended.",
                ),
            ),
        ).whenever(fixture.mutationGate)
            .requireContinuation(any(), eq(InformationRequestContinuationAction.ASSIGN_PARTY_WITHIN_CAP))
        val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext)

        assertThrows<SubscriptionDenialException> {
            fixture.assignments.assign(
                AssignInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                    principal = PrincipalRef.participant(UUID.randomUUID()),
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-while-suspended",
                ),
            )
        }
        val revoked = fixture.revocations.revoke(
            RevokeInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = existingParty.id,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "revoke-while-suspended",
            ),
        )

        assertFalse(revoked.party.active)
    }

    @Test
    fun `a party that cannot be reassigned or is already revoked is refused as a conflict with its own reason`() {
        val fixture = InformationRequestPartyFixture()
        val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext)
        val subject =
            fixture.materialization.materializeSubjectParties(fixture.request, listOf(UUID.randomUUID()), access)
                .single()
        val acting = fixture.activeActingParty()
        fun etag() = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request))
        fun revoke(key: String) = fixture.revocations.revoke(
            RevokeInformationRequestPartyCommand(fixture.request.id, acting.id, access, etag(), key),
        )

        val subjectReassignment = assertThrows<InformationRequestLifecycleException> {
            fixture.revocations.reassign(
                ReassignInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    partyId = subject.id,
                    principal = PrincipalRef.participant(UUID.randomUUID()),
                    access = access,
                    precondition = etag(),
                    idempotencyKey = "reassign-subject",
                ),
            )
        }
        revoke("first-revocation")
        val secondRevocation = assertThrows<InformationRequestLifecycleException> { revoke("second-revocation") }

        assertEquals(InformationRequestErrorCatalog.PARTY_NOT_REASSIGNABLE, subjectReassignment.reasonCode)
        assertEquals(InformationRequestErrorCatalog.PARTY_ALREADY_REVOKED, secondRevocation.reasonCode)
    }

    @Test
    fun `a subject that names a principal, or an acting party that names a subject, is refused as a malformed request`() {
        val fixture = InformationRequestPartyFixture()
        val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext)
        fun assign(roleKey: InformationRequestShareRoleKey, key: String) = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = roleKey,
            principal = PrincipalRef.participant(UUID.randomUUID()),
            subjectIdentityRefId = UUID.randomUUID(),
            access = access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = key,
        )

        val refusals = listOf(
            assign(InformationRequestShareRoleKey.SUBJECT, "subject-with-principal"),
            assign(InformationRequestShareRoleKey.CONTRIBUTOR, "acting-with-subject"),
        ).map { command -> assertThrows<InformationRequestCommandRequestException> { fixture.assignments.assign(command) } }

        refusals.forEach { assertEquals(InformationRequestErrorCatalog.PARTY_ASSIGNMENT_INVALID, it.reasonCode) }
        verify(fixture.partyRepository, never()).save(any())
    }

    @Test
    fun `every party writer records the acting principal as the canonical assigner`() {
        val fixture = InformationRequestPartyFixture()
        val application = PrincipalRef.application(UUID.randomUUID())
        val serviceAccount = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID.randomUUID())
        val user = PrincipalRef.user(UUID.randomUUID())

        fixture.assignments.assign(
            AssignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                principal = PrincipalRef.participant(UUID.randomUUID()),
                access = RequestAccessContext(application, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "assign-by-application",
            ),
        )
        val assigned = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(assigned.capture())
        assertEquals(PrincipalKind.APPLICATION, assigned.firstValue.assignedByPrincipalKind)
        assertEquals(application.id, assigned.firstValue.assignedByPrincipalId)

        fixture.revocations.reassign(
            ReassignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = assigned.firstValue.id,
                principal = PrincipalRef.participant(UUID.randomUUID()),
                access = RequestAccessContext(serviceAccount, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "reassign-by-service-account",
            ),
        )
        assertEquals(PrincipalKind.SERVICE_ACCOUNT, assigned.firstValue.assignedByPrincipalKind)
        assertEquals(serviceAccount.id, assigned.firstValue.assignedByPrincipalId)

        val defaults = fixture.materialization.materializeBlueprintDefaultParties(
            fixture.request,
            listOf(
                BlueprintInformationRequestPartyDefault(
                    InformationRequestShareRoleKey.ATTESTOR,
                    PrincipalRef.user(UUID.randomUUID())
                )
            ),
            RequestAccessContext(application, fixture.authorizationContext),
        )
        val subjects = fixture.materialization.materializeSubjectParties(
            fixture.request,
            listOf(UUID.randomUUID()),
            RequestAccessContext(user, fixture.authorizationContext),
        )
        assertEquals(
            listOf(PrincipalKind.APPLICATION to application.id),
            defaults.map { it.assignedByPrincipalKind to it.assignedByPrincipalId })
        assertEquals(
            listOf(PrincipalKind.USER to user.id),
            subjects.map { it.assignedByPrincipalKind to it.assignedByPrincipalId })
    }

    @Test
    fun `parties created with a request record one assignment each, attributed to whoever created the request`() {
        val fixture = InformationRequestPartyFixture()
        val creator = PrincipalRef.user(UUID.randomUUID())
        val access = RequestAccessContext(creator, fixture.authorizationContext)

        val defaults = fixture.materialization.materializeBlueprintDefaultParties(
            fixture.request,
            listOf(
                BlueprintInformationRequestPartyDefault(
                    InformationRequestShareRoleKey.CONTRIBUTOR,
                    PrincipalRef.user(UUID.randomUUID())
                )
            ),
            access,
        )
        val copies = fixture.materialization.copyEligibleParties(
            fixture.request,
            fixture.request,
            listOf(
                InformationRequestParty().apply {
                    roleKey = InformationRequestShareRoleKey.ATTESTOR
                    principalKind = PrincipalKind.USER
                    principalId = UUID.randomUUID()
                },
            ),
            access,
        )
        val subjects =
            fixture.materialization.materializeSubjectParties(fixture.request, listOf(UUID.randomUUID()), access)

        val created = defaults + copies.copied + subjects
        val assignments = fixture.savedTransitions.filter { it.mutation == InformationRequestMutation.ASSIGN_PARTY }
        assertEquals(3, created.size)
        assertEquals(created.map { it.id }, assignments.map { it.partyId })
        assertTrue(assignments.all { it.actorId == creator.id }, "Each assignment names the creator")
    }

    @Test
    fun `Blueprint defaults and follow-up copies hold one party for each role and principal`() {
        val fixture = InformationRequestPartyFixture()
        val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext)
        val person = PrincipalRef.user(UUID.randomUUID())
        val roles = listOf(
            InformationRequestShareRoleKey.CONTRIBUTOR,
            InformationRequestShareRoleKey.CONTRIBUTOR,
            InformationRequestShareRoleKey.ATTESTOR,
        )

        val defaults = fixture.materialization.materializeBlueprintDefaultParties(
            fixture.request,
            roles.map { BlueprintInformationRequestPartyDefault(it, person) },
            access,
        )
        val copies = fixture.materialization.copyEligibleParties(
            fixture.request,
            fixture.request,
            roles.map { role ->
                InformationRequestParty().apply {
                    roleKey = role
                    principalKind = person.kind
                    principalId = person.id
                }
            },
            access,
        )

        val expected = listOf(
            InformationRequestShareRoleKey.CONTRIBUTOR to person.id,
            InformationRequestShareRoleKey.ATTESTOR to person.id,
        )
        assertEquals(expected, defaults.map { it.roleKey to it.principalId })
        assertEquals(expected, copies.copied.map { it.roleKey to it.principalId })
        assertTrue(copies.notCopiedPartyIds.isEmpty())
    }

    @Test
    fun `a participant is not copied as a follow-up's reviewer and is refused as a Blueprint default reviewer`() {
        val fixture = InformationRequestPartyFixture()
        val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext)
        val participant = PrincipalRef(PrincipalKind.PARTICIPANT, UUID.randomUUID())
        val source = InformationRequestParty().apply {
            roleKey = InformationRequestShareRoleKey.REVIEWER
            principalKind = participant.kind
            principalId = participant.id
        }

        val copies =
            fixture.materialization.copyEligibleParties(fixture.request, fixture.request, listOf(source), access)
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.materialization.materializeBlueprintDefaultParties(
                fixture.request,
                listOf(BlueprintInformationRequestPartyDefault(InformationRequestShareRoleKey.REVIEWER, participant)),
                access,
            )
        }

        assertTrue(copies.copied.isEmpty())
        assertEquals(listOf(source.id), copies.notCopiedPartyIds)
        assertEquals(InformationRequestErrorCatalog.REVIEWER_SIGN_IN_REQUIRED, refusal.reasonCode)
    }

    @Test
    fun `ordinary, email, and reassigned parties are refused by the sharing policy before any Share is granted`() {
        val fixture = InformationRequestPartyFixture()
        val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext)
        val existingParty = fixture.activeActingParty()
        val refusal = InformationRequestLifecycleException(
            InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED,
            "Not permitted"
        )
        doThrow(refusal).whenever(fixture.eligibility).requireEligible(any(), any(), any())
        doThrow(refusal).whenever(fixture.eligibility).requireAddressEligible(any(), any(), any())
        fun precondition() = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request))

        val refusals = listOf(
            {
                fixture.assignments.assign(
                    AssignInformationRequestPartyCommand(
                        requestId = fixture.request.id,
                        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                        principal = PrincipalRef.user(UUID.randomUUID()),
                        access = access,
                        precondition = precondition(),
                        idempotencyKey = "assign-refused-by-policy",
                    ),
                )
            },
            {
                fixture.assignments.assignExternalParticipant(
                    AssignExternalParticipantInformationRequestPartyCommand(
                        requestId = fixture.request.id,
                        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                        email = "respondent@process.test",
                        access = access,
                        precondition = precondition(),
                        idempotencyKey = "email-refused-by-policy",
                    ),
                )
            },
            {
                fixture.revocations.reassign(
                    ReassignInformationRequestPartyCommand(
                        requestId = fixture.request.id,
                        partyId = existingParty.id,
                        principal = PrincipalRef.user(UUID.randomUUID()),
                        access = access,
                        precondition = precondition(),
                        idempotencyKey = "reassign-refused-by-policy",
                    ),
                )
            },
        ).map { command -> assertThrows(InformationRequestLifecycleException::class.java) { command() } }

        assertEquals(
            List(3) { InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED },
            refusals.map { it.reasonCode })
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(),
        )
        verify(fixture.shareService, never()).revoke(any(), anyOrNull(), anyOrNull())
        verify(fixture.externalParticipantService, never()).findOrCreate(any(), any(), anyOrNull())
        assertTrue(existingParty.active)
        assertEquals(1, fixture.request.partyRevision)
    }

    @Test
    fun `a finished request takes no new party and keeps the parties it has`() {
        val fixture = InformationRequestPartyFixture()
        fixture.request.state = InformationRequestState.CANCELLED
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()

        val assignment = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.assignments.assign(
                AssignInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                    principal = PrincipalRef.participant(UUID.randomUUID()),
                    access = RequestAccessContext(actor, fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-after-cancel",
                ),
            )
        }
        val revocation = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.revocations.revoke(
                RevokeInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    partyId = existingParty.id,
                    access = RequestAccessContext(actor, fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "revoke-after-cancel",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, assignment.reasonCode)
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, revocation.reasonCode)
        assertTrue(existingParty.active)
        assertTrue(fixture.savedTransitions.isEmpty())
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(),
        )
    }
}
