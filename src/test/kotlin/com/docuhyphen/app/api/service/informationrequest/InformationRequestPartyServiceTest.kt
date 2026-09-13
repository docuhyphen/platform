package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservation
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.SubjectIdentityRefRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ExternalParticipantOwner
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientSelectionResolver
import com.docuhyphen.app.api.service.exchange.ResolvedExchangeRecipientSelection
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedPersonRecipientSelectionRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestPartyServiceTest
{
    @Test
    fun `assigning an acting party materializes a request scoped Share and advances party revision`()
    {
        val fixture = Fixture()
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

        val result = fixture.service.assign(command)

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(contributor.kind, party.firstValue.principalKind)
        assertEquals(contributor.id, party.firstValue.principalId)
        assertEquals(exchangeRecipient.id, party.firstValue.exchangeRecipientId)
        assertEquals(fixture.share.id, party.firstValue.shareId)
        assertEquals(2, fixture.request.partyRevision)
        assertEquals(InformationRequestETag.partiesOf(fixture.request), result.partiesETag)
        verify(fixture.shareService).grantRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.PARTICIPANT),
            principalId = eq(contributor.id),
            roleName = eq(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            grantedByPrincipal = eq(actor),
            source = eq(ShareSource.DIRECT),
            constraintsJson = eq(null),
            expiresAt = eq(null),
            status = eq(ShareStatus.ACTIVE),
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
    fun `party mutation requires the current party ETag`()
    {
        val fixture = Fixture()
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
            fixture.service.assign(command)
        }
    }

    @Test
    fun `acting party assignment rejects an Exchange recipient from another Exchange`()
    {
        val fixture = Fixture()
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

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.assign(command)
        }
        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
        )
    }

    @Test
    fun `acting party assignment rejects an Exchange recipient bound to another principal`()
    {
        val fixture = Fixture()
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

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.assign(command)
        }
        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
        )
    }

    @Test
    fun `acting party assignment rejects unsupported principal kinds`()
    {
        val fixture = Fixture()
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
            fixture.service.assign(command)
        }
        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
        )
    }

    @Test
    fun `external contact assignment creates an owner scoped participant and materializes a request Share`()
    {
        val fixture = Fixture()
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
        ).thenReturn(com.docuhyphen.app.api.model.entity.ExternalParticipant().apply {
            id = participantId
            ownerOrganizationId = fixture.request.ownerOrganizationId
            email = "contributor@example.test"
            emailLower = "contributor@example.test"
        })

        val result = fixture.service.assignExternalParticipant(command)

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(PrincipalKind.PARTICIPANT, party.firstValue.principalKind)
        assertEquals(participantId, party.firstValue.principalId)
        assertEquals(null, party.firstValue.exchangeRecipientId)
        verify(fixture.shareService).grantRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.PARTICIPANT),
            principalId = eq(participantId),
            roleName = eq(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            grantedByPrincipal = eq(actor),
            source = eq(ShareSource.DIRECT),
            constraintsJson = eq(null),
            expiresAt = eq(null),
            status = eq(ShareStatus.ACTIVE),
            resourceLabel = eq("Information Request"),
        )
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals("assign-external-participant-information-request-party", receipt.operationName)
        assertEquals(result.partiesETag, receipt.resultETag)
    }

    @Test
    fun `external contact assignment uses a personal participant owner for personal requests`()
    {
        val fixture = Fixture()
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
        ).thenReturn(com.docuhyphen.app.api.model.entity.ExternalParticipant().apply {
            id = participantId
            ownerAppUserId = fixture.request.ownerUserId
            email = "actor@example.test"
            emailLower = "actor@example.test"
        })

        fixture.service.assignExternalParticipant(
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
    fun `trusted person selection materializes a request party from an accepted Exchange recipient`()
    {
        val fixture = Fixture()
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
        val selection = TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString())
        whenever(
            fixture.exchangeRecipientSelectionResolver.resolve(
                selection,
                fixture.initiator,
                fixture.request.ownerOrganizationId,
            ),
        ).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.APP_USER,
                selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
                appUser = trustedUser,
                targetOrganizationId = recipient.targetOrganizationId,
                preparedPersonResolution = mock(),
            ),
        )
        whenever(
            fixture.shareService.findDirectForPrincipalOnResource(
                PrincipalKind.USER,
                trustedUser.id,
                ResourceType.EXCHANGE,
                fixture.request.exchangeId,
            ),
        ).thenReturn(directExchangeShare)

        val result = fixture.service.assignTrustedRecipientSelection(
            AssignTrustedRecipientInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                selection = selection,
                initiator = fixture.initiator,
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
        verify(fixture.shareService).grantRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.USER),
            principalId = eq(trustedUser.id),
            roleName = eq(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            grantedByPrincipal = eq(actor),
            source = eq(ShareSource.DIRECT),
            constraintsJson = eq(null),
            expiresAt = eq(null),
            status = eq(ShareStatus.ACTIVE),
            resourceLabel = eq("Information Request"),
        )
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals("assign-trusted-recipient-information-request-party", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST_PARTY, receipt.resultResourceType)
        assertEquals(party.firstValue.id, receipt.resultResourceId)
    }

    @Test
    fun `trusted person selection preserves the Exchange recipient acceptance guard`()
    {
        val fixture = Fixture()
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
        val selection = TrustedPersonRecipientSelectionRequest(UUID.randomUUID().toString())
        whenever(
            fixture.exchangeRecipientSelectionResolver.resolve(
                selection,
                fixture.initiator,
                fixture.request.ownerOrganizationId,
            ),
        ).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.APP_USER,
                selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
                appUser = trustedUser,
                targetOrganizationId = recipient.targetOrganizationId,
                preparedPersonResolution = mock(),
            ),
        )
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

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.assignTrustedRecipientSelection(
                AssignTrustedRecipientInformationRequestPartyCommand(
                    requestId = fixture.request.id,
                    roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                    selection = selection,
                    initiator = fixture.initiator,
                    access = RequestAccessContext(actor, fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "assign-pending-trusted-person-selection",
                ),
            )
        }

        verify(fixture.partyRepository, never()).save(any())
        verify(fixture.shareService, never()).grantRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
        )
    }

    @Test
    fun `trusted group selection materializes a request party from an accepted Exchange recipient`()
    {
        val fixture = Fixture()
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
        val selection = TrustedGroupRecipientSelectionRequest(
            organizationId = targetOrganizationId.toString(),
            groupId = group.id.toString(),
        )
        whenever(
            fixture.exchangeRecipientSelectionResolver.resolve(
                selection,
                fixture.initiator,
                fixture.request.ownerOrganizationId,
            ),
        ).thenReturn(
            ResolvedExchangeRecipientSelection(
                recipientType = ExchangeRecipientType.GROUP,
                selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP,
                group = group,
                targetOrganizationId = targetOrganizationId,
                trustedGroupValidation = mock(),
            ),
        )
        whenever(
            fixture.shareService.findDirectForPrincipalOnResource(
                PrincipalKind.PRINCIPAL_GROUP,
                group.id,
                ResourceType.EXCHANGE,
                fixture.request.exchangeId,
            ),
        ).thenReturn(directExchangeShare)

        fixture.service.assignTrustedRecipientSelection(
            AssignTrustedRecipientInformationRequestPartyCommand(
                requestId = fixture.request.id,
                roleKey = InformationRequestShareRoleKey.REVIEWER,
                selection = selection,
                initiator = fixture.initiator,
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
        verify(fixture.shareService).grantRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.PRINCIPAL_GROUP),
            principalId = eq(group.id),
            roleName = eq(InformationRequestShareRoleKey.REVIEWER.name),
            grantedByPrincipal = eq(actor),
            source = eq(ShareSource.DIRECT),
            constraintsJson = eq(null),
            expiresAt = eq(null),
            status = eq(ShareStatus.ACTIVE),
            resourceLabel = eq("Information Request"),
        )
    }

    @Test
    fun `subject parties name only a stable subject identity and create no Share`()
    {
        val fixture = Fixture()
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

        fixture.service.assign(command)

        val party = argumentCaptor<InformationRequestParty>()
        verify(fixture.partyRepository).save(party.capture())
        assertEquals(subjectIdentityRefId, party.firstValue.subjectIdentityRefId)
        assertEquals(null, party.firstValue.principalKind)
        assertEquals(null, party.firstValue.principalId)
        assertEquals(null, party.firstValue.shareId)
        verify(fixture.shareService, never()).grantRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
        )
    }

    @Test
    fun `replaying assignment with the same idempotency key returns the stored party without another Share grant`()
    {
        val fixture = Fixture()
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

        val first = fixture.service.assign(command)
        val replay = fixture.service.assign(command)

        assertEquals(first.party.id, replay.party.id)
        assertEquals(first.partiesETag, replay.partiesETag)
        verify(fixture.shareService).grantRoleKeyWithPrincipalProvenance(
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
        )
    }

    @Test
    fun `assigning an acting party to an issued request consumes one frozen recipient slot`()
    {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            additionalRecipientCap = 1
        }
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(grant)
        val reservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                eq(grant.id),
                eq(RequestExecutionUsageKind.ADDITIONAL_RECIPIENT),
                any(),
                eq(1L),
            ),
        ).thenReturn(reservation)
        val actor = PrincipalRef.user(UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())

        fixture.service.assign(
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
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            additionalRecipientCap = 1
        }
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(grant)
        val reservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                eq(grant.id),
                eq(RequestExecutionUsageKind.ADDITIONAL_RECIPIENT),
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

        fixture.service.assign(command)
        fixture.service.assign(command)

        verify(fixture.executionUsageReservationService, times(1)).reserve(
            eq(grant.id),
            eq(RequestExecutionUsageKind.ADDITIONAL_RECIPIENT),
            any(),
            eq(1L),
        )
        verify(fixture.executionUsageReservationService, times(1)).consume(reservation.id)
    }

    @Test
    fun `issued acting party assignment releases a reservation when Share creation fails`()
    {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            additionalRecipientCap = 1
        }
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(grant)
        val reservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                eq(grant.id),
                eq(RequestExecutionUsageKind.ADDITIONAL_RECIPIENT),
                any(),
                eq(1L),
            ),
        ).thenReturn(reservation)
        whenever(
            fixture.shareService.grantRoleKeyWithPrincipalProvenance(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                anyOrNull(),
            ),
        ).thenThrow(IllegalStateException("share creation failed"))
        val actor = PrincipalRef.user(UUID.randomUUID())
        val contributor = PrincipalRef.participant(UUID.randomUUID())

        assertThrows(IllegalStateException::class.java) {
            fixture.service.assign(
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
    fun `revoking a party deactivates it revokes its Share and advances party revision`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val command = RevokeInformationRequestPartyCommand(
            requestId = fixture.request.id,
            partyId = existingParty.id,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "revoke-contributor",
        )

        val result = fixture.service.revoke(command)

        assertFalse(existingParty.active)
        assertNotNull(existingParty.revokedAt)
        assertEquals(2, existingParty.partyRevision)
        assertEquals(2, fixture.request.partyRevision)
        assertEquals(InformationRequestETag.partiesOf(fixture.request), result.partiesETag)
        verify(fixture.shareService).revokeWithPrincipalProvenance(
            shareId = eq(fixture.share.id),
            revokedByPrincipal = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals("revoke-information-request-party", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST_PARTY, receipt.resultResourceType)
        assertEquals(existingParty.id, receipt.resultResourceId)
    }

    @Test
    fun `revoking an issued acting party rolls back its consumed recipient slot`()
    {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        val actor = PrincipalRef.user(UUID.randomUUID())
        val existingParty = fixture.activeActingParty()
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            additionalRecipientCap = 1
        }
        val reservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(grant)
        whenever(
            fixture.executionUsageReservationService.reserve(
                grant.id,
                RequestExecutionUsageKind.ADDITIONAL_RECIPIENT,
                "information_request.party|${existingParty.id}",
                1L,
            ),
        ).thenReturn(reservation)

        fixture.service.revoke(
            RevokeInformationRequestPartyCommand(
                requestId = fixture.request.id,
                partyId = existingParty.id,
                access = RequestAccessContext(actor, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "revoke-issued-contributor",
            ),
        )

        verify(fixture.executionUsageReservationService).rollback(reservation.id)
    }

    @Test
    fun `reassigning a party revokes the old Share grants a new Share and advances party revision`()
    {
        val fixture = Fixture()
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

        val result = fixture.service.reassign(command)

        assertEquals(newContributor.kind, existingParty.principalKind)
        assertEquals(newContributor.id, existingParty.principalId)
        assertEquals(fixture.share.id, existingParty.shareId)
        assertEquals(2, existingParty.partyRevision)
        assertEquals(2, fixture.request.partyRevision)
        assertEquals(InformationRequestETag.partiesOf(fixture.request), result.partiesETag)
        verify(fixture.shareService).revokeWithPrincipalProvenance(
            shareId = eq(fixture.share.id),
            revokedByPrincipal = eq(actor),
            resourceLabel = eq("Information Request"),
        )
        verify(fixture.shareService).grantRoleKeyWithPrincipalProvenance(
            resourceType = eq(ResourceType.INFORMATION_REQUEST),
            resourceId = eq(fixture.request.id),
            principalKind = eq(PrincipalKind.PARTICIPANT),
            principalId = eq(newContributor.id),
            roleName = eq(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            grantedByPrincipal = eq(actor),
            source = eq(ShareSource.DIRECT),
            constraintsJson = eq(null),
            expiresAt = eq(null),
            status = eq(ShareStatus.ACTIVE),
            resourceLabel = eq("Information Request"),
        )
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals("reassign-information-request-party", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST_PARTY, receipt.resultResourceType)
        assertEquals(existingParty.id, receipt.resultResourceId)
    }

    @Test
    fun `reassigning a party while the request is active records party scoped history and audit`()
    {
        val fixture = Fixture()
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

        fixture.service.reassign(command)

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
    fun `reassigning a party rechecks the locked parent Exchange and denies when it is terminal`()
    {
        val fixture = Fixture(parentStatus = ExchangeStatus.ENDED)
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
            fixture.service.reassign(command)
        }

        assertEquals(InformationRequestErrorCatalog.PARENT_STATE_INVALID, failure.reasonCode)
        assertEquals(originalPrincipalId, existingParty.principalId)
        assertEquals(1, existingParty.partyRevision)
        assertTrue(fixture.savedTransitions.isEmpty())
        verify(fixture.shareService, never()).revokeWithPrincipalProvenance(
            shareId = any(),
            revokedByPrincipal = anyOrNull(),
            resourceLabel = anyOrNull(),
        )
    }

    private class Fixture(parentStatus: ExchangeStatus = ExchangeStatus.ACCEPTED_STARTED)
    {
        private val savedParties = mutableMapOf<UUID, InformationRequestParty>()
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            partyRevision = 1
        }
        val share = Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST
            resourceId = request.id
            principalKind = PrincipalKind.PARTICIPANT
            principalId = UUID.randomUUID()
            roleName = InformationRequestShareRoleKey.CONTRIBUTOR.name
        }
        private val exchange = Exchange().apply {
            id = request.exchangeId
            ownerOrganizationId = request.ownerOrganizationId
            status = parentStatus
            isDeleted = false
        }
        val authorizationContext = com.docuhyphen.app.api.service.auth.authz.AuthorizationContext(
            activeOrgId = request.ownerOrganizationId,
        )
        val requestRepository = mock<InformationRequestRepository>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val subjectIdentityRefRepository = mock<SubjectIdentityRefRepository>()
        val externalParticipantService = mock<ExternalParticipantService>()
        val exchangeRecipientService = mock<ExchangeRecipientService>()
        val exchangeRecipientSelectionResolver = mock<ExchangeRecipientSelectionResolver>()
        val shareService = mock<ShareService>()
        val authorizationService = mock<AuthorizationService>()
        val exchangeRepository = mock<ExchangeRepository>()
        val transitionRepository = mock<InformationRequestTransitionRepository>()
        val executionGrantService = mock<InformationRequestExecutionGrantService>()
        val executionUsageReservationService = mock<InformationRequestExecutionUsageReservationService>()
        val auditRecorder = mock<AuditRecorder>()
        val receiptStore = InMemoryCommandReceiptStore()
        val commandReceiptService = CommandReceiptService(receiptStore)
        val savedTransitions = mutableListOf<InformationRequestTransition>()
        val events = mutableListOf<DomainEvent>()
        private val eventPublisher = CapturingPartyDomainEventPublisher(events)
        private val transitionHistory = InformationRequestTransitionHistoryService(
            transitionRepository = transitionRepository,
            auditRecorder = auditRecorder,
            domainEventPublisher = eventPublisher,
        )
        val initiator = AppUser().apply {
            id = UUID.randomUUID()
            email = "initiator@example.test"
        }
        val service = InformationRequestPartyService(
            requestRepository = requestRepository,
            partyRepository = partyRepository,
            subjectIdentityRefRepository = subjectIdentityRefRepository,
            externalParticipantService = externalParticipantService,
            exchangeRecipientService = exchangeRecipientService,
            exchangeRecipientSelectionResolver = exchangeRecipientSelectionResolver,
            shareService = shareService,
            authorizationService = authorizationService,
            commandReceiptService = commandReceiptService,
            exchangeRepository = exchangeRepository,
            transitionHistory = transitionHistory,
            executionGrantService = executionGrantService,
            executionUsageReservationService = executionUsageReservationService,
        )

        init
        {
            whenever(exchangeRepository.findByIdForUpdate(request.exchangeId)).thenReturn(exchange)
            whenever(transitionRepository.nextSequenceNumber(request.id)).thenAnswer { savedTransitions.size + 1 }
            whenever(transitionRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestTransition>(0).also { transition -> savedTransitions += transition }
            }
            whenever(auditRecorder.record(any())).thenReturn(
                AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()),
            )
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(requestRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(partyRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestParty>(0).also { party ->
                    savedParties[party.id] = party
                }
            }
            whenever(partyRepository.update(any())).thenAnswer {
                it.getArgument<InformationRequestParty>(0).also { party ->
                    savedParties[party.id] = party
                }
            }
            whenever(partyRepository.findById(any())).thenAnswer {
                savedParties[it.getArgument(0)]
            }
            whenever(partyRepository.findByIdForUpdate(any())).thenAnswer {
                savedParties[it.getArgument(0)]
            }
            whenever(
                shareService.grantRoleKeyWithPrincipalProvenance(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    anyOrNull(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    any(),
                    anyOrNull(),
                ),
            )
                .thenReturn(share)
            whenever(
                authorizationService.authorize(
                    any(),
                    eq(Action.INFORMATION_REQUEST_MANAGE_PARTIES),
                    any<ResourceRef>(),
                    any(),
                ),
            ).thenReturn(Decision.Allow())
            whenever(subjectIdentityRefRepository.findOwned(any(), any(), any())).thenReturn(mock())
        }

        fun activeActingParty(): InformationRequestParty =
            InformationRequestParty().apply {
                informationRequestId = request.id
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
                principalKind = PrincipalKind.PARTICIPANT
                principalId = UUID.randomUUID()
                shareId = share.id
            }.also { party ->
                savedParties[party.id] = party
            }

        fun exchangeRecipient(
            directShareId: UUID = UUID.randomUUID(),
            exchangeId: UUID = request.exchangeId,
        ): ExchangeRecipient =
            ExchangeRecipient().apply {
                this.exchangeId = exchangeId
                this.directShareId = directShareId
            }.also { recipient ->
                whenever(exchangeRecipientService.getById(recipient.id)).thenReturn(recipient)
                whenever(exchangeRecipientService.findByDirectShareId(directShareId)).thenReturn(recipient)
                whenever(
                    exchangeRecipientService.requireAssignablePartyRecipient(
                        eq(recipient.id),
                        eq(exchangeId),
                        any<PrincipalRef>(),
                    ),
                ).thenReturn(recipient)
            }
    }
}

private class InMemoryCommandReceiptStore : CommandReceiptStore
{
    val receipts = mutableListOf<CommandReceipt>()

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                it.resourceId == request.resource.id &&
                it.operationName == request.operation &&
                it.actorKind == request.actor.kind &&
                it.actorId == request.actor.id &&
                it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}

private class CapturingPartyDomainEventPublisher(
    private val events: MutableList<DomainEvent>,
) : DomainEventPublisher
{
    override fun publish(event: DomainEvent)
    {
        events += event
    }
}
