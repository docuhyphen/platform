package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.exchange.ExternalParticipantOwner
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.party.AssignExternalParticipantInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestPartyAssignmentServiceTest
{
    @Test
    fun `assigning an acting party materializes a request scoped Share and advances party revision`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())
        val exchangeRecipient = fixture.exchangeRecipient()
        val command = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
            principal = contributor,
            exchangeRecipientId = exchangeRecipient.id,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "assign-contributor",
        )

        val result = fixture.assignments.assign(command)

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(contributor.kind, party.firstValue.principalKind)
        assertEquals(contributor.id, party.firstValue.principalId)
        assertEquals(exchangeRecipient.id, party.firstValue.exchangeRecipientId)
        assertEquals(fixture.share.id, party.firstValue.shareId)
        assertEquals(2, fixture.request.partyRevision)
        assertEquals(InformationRequestETag.partiesOf(fixture.request), result.partiesETag)
        verify(fixture.shareService).grantAdditionalRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.PARTICIPANT),
            principalId = eq(contributor.id),
            roleName = eq(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            grantedByPrincipal = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals(ResourceType.INFORMATION_REQUEST, receipt.resourceType)
        assertEquals(fixture.request.id, receipt.resourceId)
        assertEquals("assign-information-request-party", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST_PARTY, receipt.resultResourceType)
        assertEquals(party.firstValue.id, receipt.resultResourceId)
        assertEquals(result.partiesETag, receipt.resultETag)
    }

    @Test
    fun `a principal who could not answer a collected Field of its role is refused before any Share is granted`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val outsider = PrincipalRef.user(UUID.randomUUID())
        whenever(
            fixture.fieldAudience.requireAnswerableBy(
                fixture.request,
                InformationRequestShareRoleKey.CONTRIBUTOR,
                outsider
            ),
        ).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE,
                "outside"
            )
        )

        val refused = assertThrows<InformationRequestLifecycleException> {
            fixture.assignments.assign(
                AssignInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                    principal = outsider,
                    access = RequestAccessContext(actor, fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-outside-audience",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, refused.reasonCode)
        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(),
        )
        assertEquals(1, fixture.request.partyRevision)
    }

    @Test
    fun `an external contact who could not answer a collected Field of its role is refused before any Share is granted`() {
        val fixture = InformationRequestPartyFixture()
        val participantId = UUID.randomUUID()
        whenever(fixture.externalParticipantService.findOrCreate(any(), any(), anyOrNull())).thenReturn(
            ExternalParticipant().apply {
                id = participantId
                ownerOrganizationId = fixture.request.ownerOrganizationId
                email = "contributor@example.test"
                emailLower = "contributor@example.test"
            },
        )
        whenever(
            fixture.fieldAudience.requireAnswerableBy(
                fixture.request,
                InformationRequestShareRoleKey.CONTRIBUTOR,
                PrincipalRef.participant(participantId),
            ),
        ).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE,
                "outside"
            )
        )

        val refused = assertThrows<InformationRequestLifecycleException> {
            fixture.assignments.assignExternalParticipant(
                AssignExternalParticipantInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                    email = "contributor@example.test",
                    displayName = "Contributor",
                    access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-external-outside-audience",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, refused.reasonCode)
        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(),
        )
    }

    @Test
    fun `party mutation requires the current party ETag`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val command = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.REVIEWER,
            principal = PrincipalRef.user(UUID.randomUUID()),
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.Absent,
            idempotencyKey = "assign-reviewer",
        )

        assertThrows(com.docuhyphen.app.api.service.command.CommandPreconditionException::class.java) {
            fixture.assignments.assign(command)
        }
    }

    @Test
    fun `acting party assignment rejects an Exchange recipient from another Exchange`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val exchangeRecipient = fixture.exchangeRecipient(exchangeId = UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())
        whenever(
            fixture.exchangeRecipientService.requireAssignablePartyRecipient(
                exchangeRecipient.id,
                fixture.request.exchangeId,
                contributor,
            ),
        ).thenThrow(IllegalArgumentException("Exchange recipient belongs to a different Exchange"))
        val command = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
            principal = contributor,
            exchangeRecipientId = exchangeRecipient.id,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "assign-cross-exchange-recipient",
        )

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.assignments.assign(command)
        }
        assertEquals(InformationRequestErrorCatalog.TRUSTED_RECIPIENT_UNAVAILABLE, refusal.reasonCode)
        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `acting party assignment rejects an Exchange recipient bound to another principal`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())
        val exchangeRecipient = fixture.exchangeRecipient()
        whenever(
            fixture.exchangeRecipientService.requireAssignablePartyRecipient(
                exchangeRecipient.id,
                fixture.request.exchangeId,
                contributor,
            ),
        ).thenThrow(IllegalArgumentException("Exchange recipient principal does not match the request party"))
        val command = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
            principal = contributor,
            exchangeRecipientId = exchangeRecipient.id,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "assign-mismatched-recipient",
        )

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.assignments.assign(command)
        }
        assertEquals(InformationRequestErrorCatalog.TRUSTED_RECIPIENT_UNAVAILABLE, refusal.reasonCode)
        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `acting party assignment rejects unsupported principal kinds`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val command = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
            principal = PrincipalRef.publicLink(UUID.randomUUID()),
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "assign-public-link-party",
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.assignments.assign(command)
        }
        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `a reviewer added by email on an Exchange that does not require sign-in is refused before any participant exists`() {
        val fixture = InformationRequestPartyFixture()

        val refused = assertThrows<InformationRequestLifecycleException> {
            fixture.assignments.assignExternalParticipant(
                AssignExternalParticipantInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.REVIEWER,
                    email = "reviewer@example.test",
                    access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-reviewer-by-email",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.REVIEWER_SIGN_IN_REQUIRED, refused.reasonCode)
        verify(fixture.externalParticipantService, never()).findOrCreate(any(), any(), anyOrNull())
        verify(fixture.partyRepository, never()).save(any())
    }

    @Test
    fun `a participant principal cannot hold the reviewer role`() {
        val fixture = InformationRequestPartyFixture()

        val refused = assertThrows<InformationRequestLifecycleException> {
            fixture.assignments.assign(
                AssignInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.REVIEWER,
                    principal = PrincipalRef.participant(UUID.randomUUID()),
                    access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-participant-reviewer",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.REVIEWER_SIGN_IN_REQUIRED, refused.reasonCode)
        verify(fixture.partyRepository, never()).save(any())
    }

    @Test
    fun `adding an external contact never creates an account for it`() {
        val fixture = InformationRequestPartyFixture()
        whenever(fixture.externalParticipantService.findOrCreate(any(), any(), anyOrNull()))
            .thenReturn(ExternalParticipant().apply { id = UUID.randomUUID() })

        fixture.assignments.assignExternalParticipant(
            AssignExternalParticipantInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                email = "contact@example.test",
                access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "assign-contact-no-account",
            ),
        )

        verifyNoInteractions(fixture.appUserService)
    }

    @Test
    fun `the retained record owner role cannot be held by a party`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())

        assertThrows(InformationRequestCommandRequestException::class.java) {
            fixture.assignments.assign(
                AssignInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.RECORD_OWNER,
                    principal = PrincipalRef.user(UUID.randomUUID()),
                    access = RequestAccessContext(actor, fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-record-owner",
                ),
            )
        }
        verify(fixture.partyRepository, never()).save(any())
    }

    @Test
    fun `an address entry is checked against Trusted Organization membership before any participant or Share is created`() {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        doThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.TRUSTED_RECIPIENT_SELECTION_REQUIRED,
                "Choose this person from the trusted recipients",
            ),
        ).whenever(fixture.eligibility).requireUntrustedAddress(fixture.request, " member@partner.example.test ")

        val refusal =
            assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.assignments.assignExternalParticipant(
                    AssignExternalParticipantInformationRequestPartyCommand(
                        requestId = fixture.request.id,
                        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                        email = " member@partner.example.test ",
                        access = RequestAccessContext(actor, fixture.authorizationContext),
                        precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                        idempotencyKey = "assign-trusted-member-by-address",
                    ),
                )
            }

        assertEquals(
            InformationRequestErrorCatalog.TRUSTED_RECIPIENT_SELECTION_REQUIRED,
            refusal.reasonCode,
        )
        verify(fixture.externalParticipantService, never()).findOrCreate(any(), any(), anyOrNull())
        verify(fixture.partyRepository, never()).save(any())
    }

    @Test
    fun `external contact assignment creates an owner scoped participant and materializes a request Share`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val participantId = UUID.randomUUID()
        val command = AssignExternalParticipantInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
            email = " contributor@example.test ",
            displayName = "Contributor",
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "assign-external-contact",
        )
        whenever(
            fixture.externalParticipantService.findOrCreate(
                ExternalParticipantOwner.Organization(requireNotNull(fixture.request.ownerOrganizationId)),
                " contributor@example.test ",
                "Contributor",
            ),
        ).thenReturn(ExternalParticipant().apply {
            id = participantId
            ownerOrganizationId = fixture.request.ownerOrganizationId
            email = "contributor@example.test"
            emailLower = "contributor@example.test"
        })

        val result = fixture.assignments.assignExternalParticipant(command)

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(PrincipalKind.PARTICIPANT, party.firstValue.principalKind)
        assertEquals(participantId, party.firstValue.principalId)
        assertEquals(null, party.firstValue.exchangeRecipientId)
        verify(fixture.shareService).grantAdditionalRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.PARTICIPANT),
            principalId = eq(participantId),
            roleName = eq(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            grantedByPrincipal = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals("assign-external-participant-information-request-party", receipt.operationName)
        assertEquals(result.partiesETag, receipt.resultETag)
    }

    @Test
    fun `external contact assignment uses a personal participant owner for personal requests`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val participantId = UUID.randomUUID()
        fixture.request.ownerType = InformationRequestOwnerType.USER
        fixture.request.ownerUserId = UUID.randomUUID()
        fixture.request.ownerOrganizationId = null
        whenever(
            fixture.externalParticipantService.findOrCreate(
                ExternalParticipantOwner.Personal(requireNotNull(fixture.request.ownerUserId)),
                "actor@example.test",
                null,
            ),
        ).thenReturn(ExternalParticipant().apply {
            id = participantId
            ownerAppUserId = fixture.request.ownerUserId
            email = "actor@example.test"
            emailLower = "actor@example.test"
        })

        fixture.assignments.assignExternalParticipant(
            AssignExternalParticipantInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.PREPARER,
                email = "actor@example.test",
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "assign-personal-external-contact",
            ),
        )

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(PrincipalKind.PARTICIPANT, party.firstValue.principalKind)
        assertEquals(participantId, party.firstValue.principalId)
    }

    @Test
    fun `email assignment on a sign-in-required Exchange names only one of its recipients and answers alike otherwise`() {
        val fixture = InformationRequestPartyFixture()
        fixture.requireRecipientSignIn()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val registered = AppUser().apply {
            id = UUID.randomUUID()
            email = "registered@example.test"
        }
        whenever(fixture.appUserService.findByEmail("registered@example.test")).thenReturn(registered)
        whenever(fixture.appUserService.getById(registered.id)).thenReturn(registered)
        val command = { email: String, key: String ->
            AssignExternalParticipantInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                email = email,
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = key,
            )
        }

        val refusals = listOf(
            assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.assignments.assignExternalParticipant(command("registered@example.test", "assign-registered"))
            },
            assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.assignments.assignExternalParticipant(command("nobody@example.test", "assign-unknown"))
            },
        )

        assertEquals(
            List(2) { InformationRequestErrorCatalog.PARTY_EMAIL_NOT_RECIPIENT },
            refusals.map { it.reasonCode },
        )
        assertEquals(1, refusals.map { it.message }.distinct().size)
        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.appUserService, never()).findByEmail(any())
    }

    @Test
    fun `email assignment on a sign-in-required Exchange grants the matching account access`()
    {
        val fixture = InformationRequestPartyFixture()
        fixture.requireRecipientSignIn()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val recipient = AppUser().apply {
            id = UUID.randomUUID()
            email = "recipient@example.test"
        }
        whenever(fixture.shareService.activeUserPrincipalIds(ResourceType.EXCHANGE, fixture.request.exchangeId))
            .thenReturn(listOf(recipient.id))
        whenever(fixture.appUserService.getById(recipient.id)).thenReturn(recipient)

        fixture.assignments.assignExternalParticipant(
            AssignExternalParticipantInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                email = "recipient@example.test",
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "assign-signed-in-recipient",
            ),
        )

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(PrincipalKind.USER, party.firstValue.principalKind)
        assertEquals(recipient.id, party.firstValue.principalId)
        verify(fixture.externalParticipantService, never()).findOrCreate(any(), any(), anyOrNull())
    }

    @Test
    fun `assigning a person who is a trusted recipient of the parent Exchange binds that recipient and runs its trust checks`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val trustedUser = AppUser().apply {
            id = UUID.randomUUID()
            email = "trusted.person@example.test"
        }
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
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.ACCEPTED
            targetOrganizationId = UUID.randomUUID()
        }
        whenever(
            fixture.shareService.findDirectForPrincipalOnResource(
                PrincipalKind.USER,
                trustedUser.id,
                ResourceType.EXCHANGE,
                fixture.request.exchangeId,
            ),
        ).thenReturn(directExchangeShare)

        val result = fixture.assignments.assign(
            AssignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                principal = PrincipalRef.user(trustedUser.id),
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "assign-trusted-person-selection",
            ),
        )

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(PrincipalKind.USER, party.firstValue.principalKind)
        assertEquals(trustedUser.id, party.firstValue.principalId)
        assertEquals(recipient.id, party.firstValue.exchangeRecipientId)
        assertEquals(fixture.share.id, party.firstValue.shareId)
        assertEquals(InformationRequestETag.partiesOf(fixture.request), result.partiesETag)
        verify(fixture.exchangeRecipientService).requireAssignablePartyRecipient(
            recipient.id,
            fixture.request.exchangeId,
            PrincipalRef.user(trustedUser.id),
        )
        verify(fixture.shareService).grantAdditionalRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.USER),
            principalId = eq(trustedUser.id),
            roleName = eq(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            grantedByPrincipal = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals("assign-information-request-party", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST_PARTY, receipt.resultResourceType)
        assertEquals(party.firstValue.id, receipt.resultResourceId)
    }

    @Test
    fun `assigning a trusted person whose Exchange invitation is not yet accepted is refused before any Share is granted`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val trustedUser = AppUser().apply {
            id = UUID.randomUUID()
            email = "pending.person@example.test"
        }
        val directExchangeShare = Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.EXCHANGE
            resourceId = fixture.request.exchangeId
            principalKind = PrincipalKind.USER
            principalId = trustedUser.id
            roleName = ExchangeShareRoleName.VIEWER.name
            source = ShareSource.DIRECT
            status = ShareStatus.PENDING_APPROVAL
        }
        val recipient = fixture.exchangeRecipient(directExchangeShare.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
            targetOrganizationId = UUID.randomUUID()
        }
        whenever(
            fixture.shareService.findDirectForPrincipalOnResource(
                PrincipalKind.USER,
                trustedUser.id,
                ResourceType.EXCHANGE,
                fixture.request.exchangeId,
            ),
        ).thenReturn(directExchangeShare)
        whenever(
            fixture.exchangeRecipientService.requireAssignablePartyRecipient(
                recipient.id,
                fixture.request.exchangeId,
                PrincipalRef.user(trustedUser.id),
            ),
        ).thenThrow(IllegalArgumentException("Trusted recipient invitation must be accepted before request party assignment"))

        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.assignments.assign(
                AssignInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                    principal = PrincipalRef.user(trustedUser.id),
                    access = RequestAccessContext(actor, fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-pending-trusted-person-selection",
                ),
            )
        }

        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `assigning a published group that is a trusted recipient of the parent Exchange binds that recipient`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val group = PrincipalGroup().apply {
            id = UUID.randomUUID()
            name = "Published response group"
        }
        val targetOrganizationId = UUID.randomUUID()
        val directExchangeShare = Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.EXCHANGE
            resourceId = fixture.request.exchangeId
            principalKind = PrincipalKind.PRINCIPAL_GROUP
            principalId = group.id
            roleName = ExchangeShareRoleName.VIEWER.name
            source = ShareSource.DIRECT
            status = ShareStatus.ACTIVE
        }
        val recipient = fixture.exchangeRecipient(directExchangeShare.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.ACCEPTED
            this.targetOrganizationId = targetOrganizationId
        }
        whenever(
            fixture.shareService.findDirectForPrincipalOnResource(
                PrincipalKind.PRINCIPAL_GROUP,
                group.id,
                ResourceType.EXCHANGE,
                fixture.request.exchangeId,
            ),
        ).thenReturn(directExchangeShare)

        fixture.assignments.assign(
            AssignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.REVIEWER,
                principal = PrincipalRef.group(group.id),
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "assign-trusted-group-selection",
            ),
        )

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(PrincipalKind.PRINCIPAL_GROUP, party.firstValue.principalKind)
        assertEquals(group.id, party.firstValue.principalId)
        assertEquals(recipient.id, party.firstValue.exchangeRecipientId)
        verify(fixture.exchangeRecipientService).requireAssignablePartyRecipient(
            recipient.id,
            fixture.request.exchangeId,
            PrincipalRef.group(group.id),
        )
        verify(fixture.shareService).grantAdditionalRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.PRINCIPAL_GROUP),
            principalId = eq(group.id),
            roleName = eq(InformationRequestShareRoleKey.REVIEWER.name),
            grantedByPrincipal = eq(actor),
            resourceLabel = eq("Information Request"),
        )
    }

    @Test
    fun `subject parties name only a stable subject identity and create no Share`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val subjectIdentityRefId = UUID.randomUUID()
        val command = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.SUBJECT,
            subjectIdentityRefId = subjectIdentityRefId,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "assign-subject",
        )

        fixture.assignments.assign(command)

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(subjectIdentityRefId, party.firstValue.subjectIdentityRefId)
        assertEquals(null, party.firstValue.principalKind)
        assertEquals(null, party.firstValue.principalId)
        assertEquals(null, party.firstValue.shareId)
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `replaying assignment with the same idempotency key returns the stored party without another Share grant`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())
        val command = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
            principal = contributor,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "assign-once",
        )

        val first = fixture.assignments.assign(command)
        val replay = fixture.assignments.assign(command)

        assertEquals(first.party.id, replay.party.id)
        assertEquals(first.partiesETag, replay.partiesETag)
        verify(fixture.shareService).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `assigning an acting party to an issued request consumes one frozen recipient slot`()
    {
        val fixture = InformationRequestPartyFixture()
        fixture.request.state = InformationRequestState.ISSUED
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            actingPartyCap = 1
        }
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(grant)
        val reservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                eq(grant.id),
                eq(RequestExecutionUsageKind.ACTING_PARTY),
                any(),
                eq(1L),
            ),
        ).thenReturn(reservation)
        val actor = PrincipalRef.user(UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())

        fixture.assignments.assign(
            AssignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                principal = contributor,
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "assign-issued-contributor",
            ),
        )

        verify(fixture.executionUsageReservationService).consume(reservation.id)
    }

    @Test
    fun `replaying an issued acting party assignment does not reserve capacity twice`()
    {
        val fixture = InformationRequestPartyFixture()
        fixture.request.state = InformationRequestState.ISSUED
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            actingPartyCap = 1
        }
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(grant)
        val reservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                eq(grant.id),
                eq(RequestExecutionUsageKind.ACTING_PARTY),
                any(),
                eq(1L),
            ),
        ).thenReturn(reservation)
        val actor = PrincipalRef.user(UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())
        val command = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
            principal = contributor,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "assign-issued-contributor-once",
        )

        fixture.assignments.assign(command)
        fixture.assignments.assign(command)

        verify(fixture.executionUsageReservationService, times(1)).reserve(
            eq(grant.id),
            eq(RequestExecutionUsageKind.ACTING_PARTY),
            any(),
            eq(1L),
        )
        verify(fixture.executionUsageReservationService, times(1)).consume(reservation.id)
    }

    @Test
    fun `issued acting party assignment releases a reservation when Share creation fails`()
    {
        val fixture = InformationRequestPartyFixture()
        fixture.request.state = InformationRequestState.ISSUED
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            actingPartyCap = 1
        }
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(grant)
        val reservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                eq(grant.id),
                eq(RequestExecutionUsageKind.ACTING_PARTY),
                any(),
                eq(1L),
            ),
        ).thenReturn(reservation)
        whenever(
            fixture.shareService.grantAdditionalRoleKeyWithPrincipalProvenance(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenThrow(IllegalStateException("share creation failed"))
        val actor = PrincipalRef.user(UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())

        assertThrows(IllegalStateException::class.java) {
            fixture.assignments.assign(
                AssignInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                    principal = contributor,
                    access = RequestAccessContext(actor, fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-issued-contributor-release",
                ),
            )
        }

        verify(fixture.executionUsageReservationService).release(reservation.id)
        verify(fixture.partyRepository, never()).save(any())
    }

    @Test
    fun `a suspended trust relationship refuses a trusted recipient with a stable reason`()
    {
        val fixture = InformationRequestPartyFixture()
        val recipient = fixture.exchangeRecipient()
        whenever(fixture.exchangeRecipientService.trustSuspended(recipient.id)).thenReturn(true)

        val refusal = assertThrows<InformationRequestLifecycleException> {
            fixture.assignments.assign(assignRecipient(fixture, recipient.id, "assign-suspended-trust"))
        }

        assertEquals(InformationRequestErrorCatalog.TRUST_SUSPENDED, refusal.reasonCode)
        verify(fixture.shareService, never()).grantAdditionalRoleKeyWithPrincipalProvenance(
            any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(),
        )
    }

    @Test
    fun `any other unavailable trusted recipient is refused with a stable reason`()
    {
        val fixture = InformationRequestPartyFixture()
        val recipient = fixture.exchangeRecipient()
        whenever(fixture.exchangeRecipientService.requireAssignablePartyRecipient(eq(recipient.id), any(), any()))
            .thenThrow(OrganizationTrustNotFoundException("Published trusted group is unavailable"))

        val refusal = assertThrows<InformationRequestLifecycleException> {
            fixture.assignments.assign(assignRecipient(fixture, recipient.id, "assign-unavailable-trust"))
        }

        assertEquals(InformationRequestErrorCatalog.TRUSTED_RECIPIENT_UNAVAILABLE, refusal.reasonCode)
    }

    @Test
    fun `a recipient whose Exchange access was withdrawn is refused with a stable reason`() {
        val fixture = InformationRequestPartyFixture()
        val recipient = fixture.exchangeRecipient()
        whenever(fixture.exchangeRecipientService.requireAssignablePartyRecipient(eq(recipient.id), any(), any()))
            .thenThrow(IllegalArgumentException("Exchange recipient Share is not eligible for acceptance"))

        val refusal = assertThrows<InformationRequestLifecycleException> {
            fixture.assignments.assign(assignRecipient(fixture, recipient.id, "assign-withdrawn-recipient"))
        }

        assertEquals(InformationRequestErrorCatalog.TRUSTED_RECIPIENT_UNAVAILABLE, refusal.reasonCode)
    }

    @Test
    fun `an address named as the subject is refused as invalid input before anything is written`() {
        val fixture = InformationRequestPartyFixture()

        val refused = assertThrows<InformationRequestCommandRequestException> {
            fixture.assignments.assignExternalParticipant(
                AssignExternalParticipantInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.SUBJECT,
                    email = "subject@example.test",
                    access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-subject-by-email",
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.PARTY_ASSIGNMENT_INVALID, refused.reasonCode)
        verify(fixture.partyRepository, never()).save(any())
    }

    private fun assignRecipient(fixture: InformationRequestPartyFixture, recipientId: UUID, key: String) =
        AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
            principal = PrincipalRef.user(UUID.randomUUID()),
            exchangeRecipientId = recipientId,
            access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = key,
        )

    @Test
    fun `assigning a party and a contact records party scoped history and audit`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        whenever(fixture.externalParticipantService.findOrCreate(any(), eq("contact@example.test"), anyOrNull()))
            .thenReturn(ExternalParticipant().apply { id = UUID.randomUUID() })

        val assigned = fixture.assignments.assign(
            AssignInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.DECISION_MAKER,
                principal = actor,
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "assign-decision-maker",
            ),
        )
        val contact = fixture.assignments.assignExternalParticipant(
            AssignExternalParticipantInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                email = "contact@example.test",
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "assign-contact",
            ),
        )

        assertEquals(
            listOf(InformationRequestMutation.ASSIGN_PARTY, InformationRequestMutation.ASSIGN_PARTY),
            fixture.savedTransitions.map { it.mutation },
        )
        assertEquals(listOf(assigned.party.id, contact.party.id), fixture.savedTransitions.map { it.partyId })
        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder, times(2)).record(audit.capture())
        assertTrue(audit.allValues.all { it.eventTypeKey == AuditEventType.INFORMATION_REQUEST_PARTY_ASSIGN.key })
    }

    @Test
    fun `assigning an acting party locks the parent Exchange before the request row`()
    {
        val fixture = InformationRequestPartyFixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())
        val command = AssignInformationRequestPartyCommand(
            requestId = fixture.request.id,
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
            principal = contributor,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "assign-lock-order",
        )

        fixture.assignments.assign(command)

        val locks = inOrder(fixture.exchangeLookup, fixture.requestRepository)
        locks.verify(fixture.exchangeLookup).lockExchangeForUpdate(fixture.request.exchangeId)
        locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
    }
}
