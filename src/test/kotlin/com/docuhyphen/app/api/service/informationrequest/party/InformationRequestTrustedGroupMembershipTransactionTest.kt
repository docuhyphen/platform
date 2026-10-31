package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestAmendmentView
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.organization.PrincipalGroupMemberSpec
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTrustedGroupMembershipTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var authorization: AuthorizationService

    @Inject
    lateinit var shareService: ShareService

    @Inject
    lateinit var recipients: ExchangeRecipientService

    @Inject
    lateinit var attestations: ExchangeRecipientAttestationService

    @Inject
    lateinit var trustValidation: TrustedRecipientValidationService

    @Inject
    lateinit var groups: PrincipalGroupService

    @Inject
    lateinit var reconciliation: TrustedGroupAccessReconciliationService

    @Inject
    lateinit var partyAssignments: InformationRequestPartyAssignmentService

    @Inject
    lateinit var requests: InformationRequestRepository

    @Test
    fun `a member who joins a trusted group while its trust is suspended reaches neither the Exchange nor the group party`() {
        val fixture = fixture()
        val joined = fixture.user()
        fixture.suspendTrust()

        fixture.join(joined)

        assertFalse(fixture.permitsExchange(joined, Action.EXCHANGE_VIEW))
        assertFalse(fixture.permitsRequest(joined, Action.INFORMATION_REQUEST_VIEW))
        assertFalse(fixture.permitsRequirement(joined, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW))
        assertFalse(fixture.permitsRequirement(joined, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
        assertEquals(0, fixture.inheritedShares(joined))
    }

    @Test
    fun `a member admitted before the suspension keeps working on the issued request`() {
        val fixture = fixture()
        fixture.suspendTrust()

        assertTrue(fixture.permitsExchange(fixture.member, Action.EXCHANGE_VIEW))
        assertTrue(fixture.permitsRequest(fixture.member, Action.INFORMATION_REQUEST_VIEW))
        assertTrue(fixture.permitsRequirement(fixture.member, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW))
        assertTrue(fixture.permitsRequirement(fixture.member, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
    }

    @Test
    fun `a member who joins after trust ended is held back while admitted members keep their work`() {
        val fixture = fixture()
        val joined = fixture.user()
        fixture.endTrust()

        fixture.join(joined)

        assertFalse(fixture.permitsRequest(joined, Action.INFORMATION_REQUEST_VIEW))
        assertFalse(fixture.permitsRequirement(joined, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
        assertTrue(fixture.permitsRequirement(fixture.member, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
    }

    @Test
    fun `a held-back member who reaches the request through a role of their own does not act for the group party`() {
        val fixture = fixture()
        val joined = fixture.user()
        val preparer = fixture.user()
        fixture.grantRequestRole(joined, InformationRequestShareRoleKey.PREPARER)
        fixture.grantRequestRole(preparer, InformationRequestShareRoleKey.PREPARER)
        fixture.suspendTrust()

        fixture.join(joined)

        assertTrue(fixture.permitsRequest(joined, Action.INFORMATION_REQUEST_VIEW))
        assertFalse(fixture.permitsRequirement(preparer, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
        assertFalse(fixture.permitsRequirement(joined, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
    }

    @Test
    fun `clearing the suspension admits the held-back member and reconciliation materializes the access`() {
        val fixture = fixture()
        val joined = fixture.user()
        val suspension = fixture.suspendTrust()
        fixture.join(joined)
        assertFalse(fixture.permitsRequirement(joined, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))

        fixture.clearSuspension(suspension)

        assertTrue(fixture.permitsRequest(joined, Action.INFORMATION_REQUEST_VIEW))
        assertTrue(fixture.permitsRequirement(joined, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
        fixture.reconcile()
        assertEquals(2, fixture.inheritedShares(joined))
    }

    @Test
    fun `removing a member ends the access the membership gave while trust is suspended`() {
        val fixture = fixture()
        fixture.suspendTrust()

        fixture.leave(fixture.member)

        assertFalse(fixture.permitsExchange(fixture.member, Action.EXCHANGE_VIEW))
        assertFalse(fixture.permitsRequest(fixture.member, Action.INFORMATION_REQUEST_VIEW))
        assertFalse(fixture.permitsRequirement(fixture.member, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND))
    }

    @Test
    fun `an account outside the trusted group reaches nothing through it`() {
        val fixture = fixture()
        val outsider = fixture.user()

        assertFalse(fixture.permitsExchange(outsider, Action.EXCHANGE_VIEW))
        assertFalse(fixture.permitsRequest(outsider, Action.INFORMATION_REQUEST_VIEW))
        assertFalse(fixture.permitsRequirement(outsider, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW))
    }

    @Inject
    lateinit var amendmentQueries: InformationRequestAmendmentQueryService

    @Test
    fun `an admitted group member sees notices owed to their party without seeing other parties notices`() {
        val fixture = fixture()
        assertTrue(fixture.permitsRequest(fixture.member, Action.INFORMATION_REQUEST_VIEW))
        assertFalse(fixture.permitsRequest(fixture.member, Action.INFORMATION_REQUEST_VIEW_OPERATIONS))
        assertEquals(1, fixture.visibleAmendmentNotices(fixture.member).size)
    }

    private fun fixture(): TrustedGroupPartyFixture {
        val fixture = dataSource.connection.use { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                runtime.exchangeId,
                runtime.template.userId,
            )
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, issued_at, created_at)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                runtime.requestId,
                runtime.template.organizationId,
            )
            val trust = TrustedGroupSqlFixture(connection, runtime.template.organizationId, runtime.template.userId)
            TrustedGroupPartyFixture(runtime, trust).also { party ->
                runtime.insertUser(party.manager)
                runtime.insertUser(party.member)
            }
        }
        fixture.inviteAndAccept()
        fixture.assignGroupParty()
        return fixture
    }

    private inner class TrustedGroupPartyFixture(
        private val runtime: SubmissionRuntimeSqlFixture,
        private val trust: TrustedGroupSqlFixture,
    ) {
        val manager: UUID = UUID.randomUUID()
        val member: UUID = UUID.randomUUID()
        private val members = mutableListOf(
            PrincipalGroupMemberSpec(manager, groupRole = PrincipalGroupRoleName.MANAGER),
            PrincipalGroupMemberSpec(member),
        )
        private val owner = RequestAccessContext(
            PrincipalRef.user(runtime.template.userId),
            AuthorizationContext(sessionRef = "owner-session", mfaSatisfied = true),
        )

        fun inviteAndAccept() {
            QuarkusTransaction.requiringNew().run { groups.syncMembers(trust.groupId, members) }
            val recipientId = QuarkusTransaction.requiringNew().call {
                val share = shareService.grant(
                    resourceType = ResourceType.EXCHANGE,
                    resourceId = runtime.exchangeId,
                    principalKind = PrincipalKind.PRINCIPAL_GROUP,
                    principalId = trust.groupId,
                    roleName = ExchangeShareRoleName.VIEWER,
                    grantedBy = owner.principal,
                    status = ShareStatus.PENDING_APPROVAL,
                )
                val recipient = recipients.createBinding(
                    exchangeId = runtime.exchangeId,
                    directShare = share,
                    purpose = ExchangeRecipientPurpose.PARTICIPANT,
                    selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP,
                    targetOrganizationId = trust.partnerOrganizationId,
                    acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING,
                )
                attestations.createGroupAttestation(
                    recipient,
                    trustValidation.validateGroupSelection(
                        trust.ownerOrganizationId,
                        trust.partnerOrganizationId,
                        trust.groupId
                    ),
                )
                recipient.id
            }
            QuarkusTransaction.requiringNew()
                .run { recipients.recordTrustedParticipantDecision(recipientId, manager, true) }
        }

        fun assignGroupParty() {
            QuarkusTransaction.requiringNew().run {
                partyAssignments.assign(
                    AssignInformationRequestPartyCommand(
                        requestId = runtime.requestId,
                        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                        principal = PrincipalRef.group(trust.groupId),
                        access = owner,
                        precondition = CommandPrecondition.ExpectedRevision(
                            InformationRequestETag.partiesOf(requireNotNull(requests.findById(runtime.requestId))),
                        ),
                        idempotencyKey = "assign-trusted-group-${UUID.randomUUID()}",
                    ),
                )
            }
        }

        fun visibleAmendmentNotices(userId: UUID): Set<UUID> = QuarkusTransaction.requiringNew().call {
            val partyId = dataSource.connection.use { connection ->
                UUID.fromString(
                    queryString(
                        connection,
                        "SELECT id::text FROM information_request_party WHERE information_request_id = ? AND principal_kind = 'PRINCIPAL_GROUP' AND principal_id = ?",
                        runtime.requestId,
                        trust.groupId
                    )
                )
            }
            val request = requireNotNull(requests.findById(runtime.requestId))
            val amendment = InformationRequestAmendment().apply { informationRequestId = request.id }
            val view = InformationRequestAmendmentView(
                amendment, emptyList(), listOf(
                    InformationRequestNoticeIntent().apply { this.partyId = partyId },
                    InformationRequestNoticeIntent().apply { this.partyId = runtime.attestorPartyId },
                )
            )
            amendmentQueries.readable(
                request,
                view,
                RequestAccessContext(
                    PrincipalRef.user(userId),
                    AuthorizationContext(sessionRef = "member-session", mfaSatisfied = true)
                )
            ).visibleNoticePartyIds
        }

        fun grantRequestRole(userId: UUID, role: InformationRequestShareRoleKey) {
            QuarkusTransaction.requiringNew().run {
                shareService.grantRoleKeyWithPrincipalProvenance(
                    resourceType = ResourceType.INFORMATION_REQUEST,
                    resourceId = runtime.requestId,
                    principalKind = PrincipalKind.USER,
                    principalId = userId,
                    roleName = role.name,
                    grantedByPrincipal = owner.principal,
                )
            }
        }

        fun user(): UUID = UUID.randomUUID().also { id ->
            dataSource.connection.use { connection ->
                execute(
                    connection,
                    """
                    INSERT INTO app_user
                        (id, is_active, created_date, email, email_verification_completed, is_temporary,
                         sign_in_attempts, exchange_version, multifactor_authentication_type,
                         is_password_temporary, email_mfa_fallback_enabled)
                    VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                    """.trimIndent(),
                    id,
                    "group-member-${id.toString().take(8)}@process.test",
                )
            }
        }

        fun join(userId: UUID) {
            members += PrincipalGroupMemberSpec(userId)
            QuarkusTransaction.requiringNew().run { groups.syncMembers(trust.groupId, members) }
        }

        fun leave(userId: UUID) {
            members.removeIf { it.principalId == userId }
            QuarkusTransaction.requiringNew().run { groups.syncMembers(trust.groupId, members) }
        }

        fun suspendTrust(): UUID = dataSource.connection.use { trust.suspend(it) }

        fun clearSuspension(suspensionId: UUID) {
            dataSource.connection.use { trust.clearSuspension(it, suspensionId) }
        }

        fun endTrust() {
            dataSource.connection.use { trust.end(it) }
        }

        fun reconcile() {
            QuarkusTransaction.requiringNew().run { reconciliation.reconcileRelationship(trust.relationshipId) }
        }

        fun permitsExchange(userId: UUID, action: Action): Boolean =
            permits(userId, action, ResourceRef.exchange(runtime.exchangeId))

        fun permitsRequest(userId: UUID, action: Action): Boolean =
            permits(userId, action, ResourceRef.informationRequest(runtime.requestId))

        fun permitsRequirement(userId: UUID, action: Action): Boolean =
            permits(userId, action, ResourceRef.informationRequestRequirement(runtime.documentRequirementId))

        fun inheritedShares(userId: UUID): Int = dataSource.connection.use { connection ->
            queryInt(
                connection,
                """
                SELECT COUNT(*) FROM share
                WHERE principal_kind = 'USER' AND principal_id = ? AND source = 'INHERITED_FROM_GROUP' AND status = 'ACTIVE'
                  AND resource_id IN (?, ?)
                """.trimIndent(),
                userId,
                runtime.exchangeId,
                runtime.requestId,
            )
        }

        private fun permits(userId: UUID, action: Action, resource: ResourceRef): Boolean =
            QuarkusTransaction.requiringNew().call {
                authorization.authorize(
                    PrincipalRef.user(userId),
                    action,
                    resource,
                    AuthorizationContext(sessionRef = "member-session", mfaSatisfied = true),
                ) is Decision.Allow
            }
    }
}
