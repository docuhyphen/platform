package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestPartyRevocationServiceTest {
    @Test
    fun `reassigning a party on a personally owned request needs no owning organization`() {
        val fixture = InformationRequestPartyFixture()
        fixture.request.ownerType = InformationRequestOwnerType.USER
        fixture.request.ownerOrganizationId = null
        fixture.request.ownerUserId = UUID.randomUUID()
        val existingParty = fixture.activeActingParty()
        val newHolder = PrincipalRef.user(UUID.randomUUID())

        fixture.revocations.reassign(fixture.reassignCommand(existingParty, newHolder, "reassign-personal"))

        assertEquals(newHolder.id, existingParty.principalId)
        verify(fixture.shareService).grantAdditionalRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.USER),
            principalId = eq(newHolder.id),
            roleName = eq(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            grantedByPrincipal = any(),
            resourceLabel = eq("Information Request"),
        )
    }

    @Test
    fun `reassigning a removed party is refused before any Share changes`() {
        val fixture = InformationRequestPartyFixture()
        val removed = fixture.activeActingParty().apply { active = false }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.revocations.reassign(
                fixture.reassignCommand(
                    removed,
                    PrincipalRef.user(UUID.randomUUID()),
                    "reassign-removed"
                )
            )
        }
        assertEquals(InformationRequestErrorCatalog.PARTY_ALREADY_REVOKED, refusal.reasonCode)
        verify(fixture.shareService, never()).revoke(any(), anyOrNull(), anyOrNull())
        verify(fixture.partyRepository, never()).update(any())
    }

    @Test
    fun `reassigning a party to someone who could not answer its collected Fields is refused before any Share changes`() {
        val fixture = InformationRequestPartyFixture()
        val existingParty = fixture.activeActingParty()
        val outsider = PrincipalRef.participant(UUID.randomUUID())
        whenever(fixture.fieldAudience.requireAnswerableBy(fixture.request, existingParty.roleKey, outsider)).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, "outside"),
        )

        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.revocations.reassign(fixture.reassignCommand(existingParty, outsider, "reassign-outside-audience"))
        }

        assertEquals(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, refused.reasonCode)
        assertNotEquals(outsider.id, existingParty.principalId)
        verify(fixture.shareService, never()).revoke(any(), anyOrNull(), anyOrNull())
        verify(fixture.partyRepository, never()).update(any())
    }

    @Test
    fun `reassignment is authorized through the central service for any caller and a refusal blocks it`() {
        val fixture = InformationRequestPartyFixture()
        val party = fixture.activeActingParty()
        val delegate = PrincipalRef.participant(UUID.randomUUID())
        val command =
            fixture.reassignCommand(party, PrincipalRef.user(UUID.randomUUID()), "reassign-by-delegate", delegate)
        whenever(
            fixture.authorizationService.authorize(
                eq(delegate),
                eq(Action.INFORMATION_REQUEST_MANAGE_PARTIES),
                any<ResourceRef>(),
                any(),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "not a manager"))

        assertThrows(io.quarkus.security.ForbiddenException::class.java) { fixture.revocations.reassign(command) }
        verify(fixture.authorizationService).authorize(
            eq(delegate),
            eq(Action.INFORMATION_REQUEST_MANAGE_PARTIES),
            eq(ResourceRef.informationRequest(fixture.request.id)),
            any(),
        )
        verify(fixture.shareService, never()).revoke(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `reassigning a party to a trusted recipient of the parent Exchange binds that recipient and runs its trust checks`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val trustedUser = PrincipalRef.user(UUID.randomUUID())
        val directExchangeShare = Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.EXCHANGE
            resourceId = fixture.request.exchangeId
            principalKind = PrincipalKind.USER
            principalId = trustedUser.id
            roleName = ExchangeShareRoleName.VIEWER.name
            source = ShareSource.DIRECT
            status = ShareStatus.ACTIVE
        }
        val recipient = fixture.exchangeRecipient(directExchangeShare.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.ACCEPTED
        }
        whenever(
            fixture.shareService.findDirectForPrincipalOnResource(
                PrincipalKind.USER,
                trustedUser.id,
                ResourceType.EXCHANGE,
                fixture.request.exchangeId,
            ),
        ).thenReturn(directExchangeShare)

        fixture.revocations.reassign(
            ReassignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = existingParty.id,
                principal = trustedUser,
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "reassign-to-trusted-person",
            ),
        )

        assertEquals(recipient.id, existingParty.exchangeRecipientId)
        verify(fixture.exchangeRecipientService).requireAssignablePartyRecipient(
            recipient.id,
            fixture.request.exchangeId,
            trustedUser,
        )
    }

    @Test
    fun `revoking a party deactivates it revokes its Share and advances party revision`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val command = RevokeInformationRequestPartyCommand(
            requestId = fixture.request.id,
            partyId = existingParty.id,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "revoke-contributor",
        )

        val result = fixture.revocations.revoke(command)

        assertFalse(existingParty.active)
        assertNotNull(existingParty.revokedAt)
        assertEquals(2, existingParty.partyRevision)
        assertEquals(2, fixture.request.partyRevision)
        assertEquals(InformationRequestETag.partiesOf(fixture.request), result.partiesETag)
        verify(fixture.shareService).revoke(
            shareId = eq(fixture.share.id),
            revokedBy = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        verify(fixture.bootstrapShareLinkService).revokeAllForParty(
            fixture.request,
            existingParty,
            InformationRequestCredentialCutoff(
                InformationRequestAuditActor.of(
                    RequestAccessContext(
                        actor,
                        fixture.authorizationContext
                    )
                ), InformationRequestCredentialCutoff.PARTY_REVOKED
            ),
        )
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals("revoke-information-request-party", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST_PARTY, receipt.resultResourceType)
        assertEquals(existingParty.id, receipt.resultResourceId)
    }

    @Test
    fun `revoking an issued acting party rolls back its consumed recipient slot`() {
        val fixture = InformationRequestPartyFixture()
        fixture.request.state = InformationRequestState.ISSUED
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            actingPartyCap = 1
        }
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(grant)

        fixture.revocations.revoke(
            RevokeInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = existingParty.id,
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "revoke-issued-contributor",
            ),
        )

        verify(fixture.executionUsageReservationService).returnCapacity(
            grant.id,
            RequestExecutionUsageKind.ACTING_PARTY,
            "information_request.party|${existingParty.id}",
        )
        verify(fixture.executionUsageReservationService, never()).reserve(any(), any(), any(), any())
    }

    @Test
    fun `revoking a subject after issuance takes no capacity it never held`() {
        val fixture = InformationRequestPartyFixture()
        fixture.request.state = InformationRequestState.ISSUED
        val subject = InformationRequestParty().apply {
            informationRequestId = fixture.request.id
            roleKey = InformationRequestShareRoleKey.SUBJECT
            subjectIdentityRefId = UUID.randomUUID()
        }
        fixture.partyRepository.save(subject)
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            RequestExecutionGrant().apply { requestId = fixture.request.id },
        )

        val revoked = fixture.revocations.revoke(
            RevokeInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = subject.id,
                access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "revoke-issued-subject",
            ),
        )

        assertFalse(revoked.party.active)
        verify(fixture.executionUsageReservationService, never()).reserve(any(), any(), any(), any())
    }

    @Test
    fun `reassigning revoking and member removal end the delegated authorities a party held`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val reassigned = fixture.activeActingParty()
        val revoked = fixture.activeActingParty()
        val removed = fixture.activeActingParty()

        fixture.revocations.reassign(
            ReassignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = reassigned.id,
                principal = PrincipalRef.participant(UUID.randomUUID()),
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "reassign-ends-authority",
            ),
        )
        fixture.revocations.revoke(fixture.revokeCommand(revoked, actor, "revoke-ends-authority"))
        fixture.revocations.revokeForOwnershipChange(fixture.request.id, removed.id)

        verify(fixture.delegatedAuthorities).revokeAllForParty(
            eq(fixture.request),
            eq(reassigned),
            eq(
                InformationRequestCredentialCutoff(
                    InformationRequestAuditActor.of(
                        RequestAccessContext(
                            actor,
                            fixture.authorizationContext
                        )
                    ), InformationRequestCredentialCutoff.PARTY_REASSIGNED
                )
            ),
        )
        verify(fixture.delegatedAuthorities).revokeAllForParty(
            eq(fixture.request),
            eq(revoked),
            eq(
                InformationRequestCredentialCutoff(
                    InformationRequestAuditActor.of(
                        RequestAccessContext(
                            actor,
                            fixture.authorizationContext
                        )
                    ), InformationRequestCredentialCutoff.PARTY_REVOKED
                )
            ),
        )
        verify(fixture.delegatedAuthorities).revokeAllForParty(
            eq(fixture.request),
            eq(removed),
            argThat { reasonCode == InformationRequestCredentialCutoff.ORGANIZATION_MEMBER_REMOVED },
        )
    }

    @Test
    fun `reassigning a party revokes the old Share grants a new Share and advances party revision`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val newContributor = PrincipalRef.participant(UUID.randomUUID())
        val command = ReassignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            partyId = existingParty.id,
            principal = newContributor,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "reassign-contributor",
        )

        val result = fixture.revocations.reassign(command)

        assertEquals(newContributor.kind, existingParty.principalKind)
        assertEquals(newContributor.id, existingParty.principalId)
        assertEquals(fixture.share.id, existingParty.shareId)
        assertEquals(2, existingParty.partyRevision)
        assertEquals(2, fixture.request.partyRevision)
        assertEquals(InformationRequestETag.partiesOf(fixture.request), result.partiesETag)
        verify(fixture.shareService).revoke(
            shareId = eq(fixture.share.id),
            revokedBy = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        verify(fixture.bootstrapShareLinkService).revokeAllForParty(
            fixture.request,
            existingParty,
            InformationRequestCredentialCutoff(
                InformationRequestAuditActor.of(
                    RequestAccessContext(
                        actor,
                        fixture.authorizationContext
                    )
                ), InformationRequestCredentialCutoff.PARTY_REASSIGNED
            ),
        )
        verify(fixture.shareService).grantAdditionalRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.PARTICIPANT),
            principalId = eq(newContributor.id),
            roleName = eq(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            grantedByPrincipal = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals("reassign-information-request-party", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST_PARTY, receipt.resultResourceType)
        assertEquals(existingParty.id, receipt.resultResourceId)
    }

    @Test
    fun `a reassignment records who held the role before and who holds it now`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val previous =
            PrincipalRef(requireNotNull(existingParty.principalKind), requireNotNull(existingParty.principalId))
        val newContributor = PrincipalRef.participant(UUID.randomUUID())

        fixture.revocations.reassign(
            ReassignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = existingParty.id,
                principal = newContributor,
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "reassign-recorded",
            ),
        )

        val payload = fixture.events.single { it.type == "information_request.party.reassign" }.payload
        assertEquals(previous.kind.name, payload["previousPrincipalKind"])
        assertEquals(previous.id.toString(), payload["previousPrincipalId"])
        assertEquals(PrincipalKind.PARTICIPANT.name, payload["principalKind"])
        assertEquals(newContributor.id.toString(), payload["principalId"])
    }

    @Test
    fun `revoking an upgraded participant party also revokes the account Share linked to it`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val accountShareId = UUID.randomUUID()
        val existingParty = fixture.activeActingParty().apply { linkedUserShareId = accountShareId }

        fixture.revocations.revoke(fixture.revokeCommand(existingParty, actor, "revoke-upgraded-contributor"))

        verify(fixture.shareService).revoke(
            shareId = eq(fixture.share.id),
            revokedBy = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        verify(fixture.shareService).revoke(
            shareId = eq(accountShareId),
            revokedBy = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        assertEquals(accountShareId, existingParty.linkedUserShareId)
    }

    @Test
    fun `reassigning an upgraded participant party revokes its account Share and clears the link`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val accountShareId = UUID.randomUUID()
        val existingParty = fixture.activeActingParty().apply { linkedUserShareId = accountShareId }
        val replacement = PrincipalRef.participant(UUID.randomUUID())

        fixture.revocations.reassign(
            ReassignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = existingParty.id,
                principal = replacement,
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "reassign-upgraded-contributor",
            ),
        )

        verify(fixture.shareService).revoke(
            shareId = eq(accountShareId),
            revokedBy = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        assertNull(existingParty.linkedUserShareId)
        assertEquals(replacement.id, existingParty.principalId)
    }

    @Test
    fun `a Share another active party still holds is kept with its links when a party that shares it is revoked`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val accountShareId = UUID.randomUUID()
        val existingParty = fixture.activeActingParty().apply { linkedUserShareId = accountShareId }
        whenever(fixture.partyRepository.existsOtherActivePartyHoldingShare(any(), any(), any())).thenReturn(true)

        fixture.revocations.revoke(fixture.revokeCommand(existingParty, actor, "revoke-sharing-contributor"))

        assertFalse(existingParty.active)
        verify(fixture.partyRepository).existsOtherActivePartyHoldingShare(
            fixture.request.id,
            fixture.share.id,
            existingParty.id
        )
        verify(fixture.partyRepository).existsOtherActivePartyHoldingShare(
            fixture.request.id,
            accountShareId,
            existingParty.id
        )
        verify(fixture.shareService, never()).revoke(any(), anyOrNull(), anyOrNull())
        verify(fixture.bootstrapShareLinkService, never()).revokeAllForParty(any(), any(), any())
    }

    @Test
    fun `reassigning a party while the request is active records party scoped history and audit`() {
        val fixture = InformationRequestPartyFixture()
        fixture.request.state = InformationRequestState.ISSUED
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val newContributor = PrincipalRef.participant(UUID.randomUUID())
        val command = ReassignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            partyId = existingParty.id,
            principal = newContributor,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "reassign-active-request",
        )

        fixture.revocations.reassign(command)

        assertEquals(1, fixture.savedTransitions.size)
        val transition = fixture.savedTransitions.single()
        assertEquals(InformationRequestMutation.REASSIGN, transition.mutation)
        assertEquals(InformationRequestState.ISSUED, transition.fromState)
        assertEquals(InformationRequestState.ISSUED, transition.toState)
        assertEquals(existingParty.id, transition.partyId)

        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder).record(audit.capture())
        assertEquals(AuditEventType.INFORMATION_REQUEST_PARTY_REASSIGN.key, audit.firstValue.eventTypeKey)
        assertEquals(1, fixture.events.size)
        assertEquals(AuditEventType.INFORMATION_REQUEST_PARTY_REASSIGN.key, fixture.events.single().type)
    }

    @Test
    fun `revoking a party records party scoped history and audit`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()

        fixture.revocations.revoke(
            RevokeInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = existingParty.id,
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "revoke-with-history",
            ),
        )

        val transition = fixture.savedTransitions.single()
        assertEquals(InformationRequestMutation.REVOKE_PARTY, transition.mutation)
        assertEquals(existingParty.id, transition.partyId)
        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder).record(audit.capture())
        assertEquals(AuditEventType.INFORMATION_REQUEST_PARTY_REVOKE.key, audit.firstValue.eventTypeKey)
    }

    @Test
    fun `reassigning or revoking a party is refused when its audit record cannot be captured`() {
        listOf("reassign", "revoke").forEach { change ->
            val fixture = InformationRequestPartyFixture()
            fixture.request.state = InformationRequestState.ISSUED
            val actor = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext)
            val existingParty = fixture.activeActingParty()
            whenever(fixture.auditRecorder.record(any()))
                .thenReturn(
                    com.docuhyphen.app.api.service.audit.AuditCaptureResult.Degraded(
                        UUID.randomUUID(),
                        "unavailable"
                    )
                )
            val precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request))

            assertThrows(com.docuhyphen.app.api.service.audit.AuditCaptureFailedException::class.java) {
                if (change == "reassign") fixture.revocations.reassign(
                    ReassignInformationRequestPartyCommand(
                        requestId = fixture.request.id,
                        partyId = existingParty.id,
                        principal = PrincipalRef.participant(UUID.randomUUID()),
                        access = actor,
                        precondition = precondition,
                        idempotencyKey = "reassign-without-audit",
                    ),
                )
                else fixture.revocations.revoke(
                    RevokeInformationRequestPartyCommand(
                        requestId = fixture.request.id,
                        partyId = existingParty.id,
                        access = actor,
                        precondition = precondition,
                        idempotencyKey = "revoke-without-audit",
                    ),
                )
            }
        }
    }

    @Test
    fun `reassigning a party locks the parent Exchange before the request row`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val newContributor = PrincipalRef.participant(UUID.randomUUID())
        val command = ReassignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            partyId = existingParty.id,
            principal = newContributor,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "reassign-lock-order",
        )

        fixture.revocations.reassign(command)

        val locks = inOrder(fixture.exchangeLookup, fixture.requestRepository)
        locks.verify(fixture.exchangeLookup).lockExchangeForUpdate(fixture.request.exchangeId)
        locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
    }

    @Test
    fun `revoking a party locks the parent Exchange before the request row`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val command = RevokeInformationRequestPartyCommand(
            requestId = fixture.request.id,
            partyId = existingParty.id,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "revoke-lock-order",
        )

        fixture.revocations.revoke(command)

        val locks = inOrder(fixture.exchangeLookup, fixture.requestRepository)
        locks.verify(fixture.exchangeLookup).lockExchangeForUpdate(fixture.request.exchangeId)
        locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
    }

    @Test
    fun `reassigning a party rechecks the locked parent Exchange and denies when it is terminal`() {
        val fixture = InformationRequestPartyFixture(parentStatus = ExchangeStatus.ENDED)
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val originalPrincipalId = existingParty.principalId
        val newContributor = PrincipalRef.participant(UUID.randomUUID())
        val command = ReassignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            partyId = existingParty.id,
            principal = newContributor,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "reassign-after-parent-end",
        )

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.revocations.reassign(command)
        }

        assertEquals(InformationRequestErrorCatalog.PARENT_STATE_INVALID, failure.reasonCode)
        assertEquals(originalPrincipalId, existingParty.principalId)
        assertEquals(1, existingParty.partyRevision)
        assertTrue(fixture.savedTransitions.isEmpty())
        verify(fixture.shareService, never()).revoke(
            shareId = any(),
            revokedBy = anyOrNull(),
            resourceLabel = anyOrNull(),
        )
    }
}
