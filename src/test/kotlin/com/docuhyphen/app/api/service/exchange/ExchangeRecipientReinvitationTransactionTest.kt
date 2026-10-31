package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.model.organization.PrincipalGroupMemberSpec
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyAssignmentService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyRevocationService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class ExchangeRecipientReinvitationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var accessManagement: ExchangeAccessManagementService

    @Inject
    lateinit var recipients: ExchangeRecipientService

    @Inject
    lateinit var shareService: ShareService

    @Inject
    lateinit var groups: PrincipalGroupService

    @Inject
    lateinit var partyAssignments: InformationRequestPartyAssignmentService

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var appUserService: AppUserService

    @Test
    fun `re-inviting a trusted group keeps the binding an active request party records`() {
        val fixture = fixture()
        val bindingId = fixture.inviteAndAccept()
        val partyId = fixture.assignGroupParty()
        fixture.revokeExchangeAccess()

        fixture.invite()

        assertEquals(bindingId, fixture.groupBindingId())
        assertEquals("PARTICIPANT:PENDING", fixture.bindingState(bindingId))
        assertEquals(1, fixture.attestations(bindingId))
        assertEquals(bindingId, fixture.recipientOf(partyId))
        assertEquals("PENDING_APPROVAL", fixture.groupShareStatus())
        fixture.accept(bindingId)
        assertEquals("PARTICIPANT:ACCEPTED", fixture.bindingState(bindingId))
    }

    @Test
    fun `re-inviting a trusted group keeps the history of a request party revoked earlier`() {
        val fixture = fixture()
        val bindingId = fixture.inviteAndAccept()
        val partyId = fixture.assignGroupParty()
        fixture.revokeParty(partyId)
        val historyBefore = fixture.partyHistory(partyId)
        fixture.revokeExchangeAccess()

        fixture.invite()

        assertEquals(bindingId, fixture.groupBindingId())
        assertEquals(historyBefore, fixture.partyHistory(partyId))
        assertEquals("PARTICIPANT:PENDING", fixture.bindingState(bindingId))
    }

    @Test
    fun `re-inviting a trusted group whose binding no request party records still starts a fresh invitation`() {
        val fixture = fixture()
        fixture.inviteAndAccept()
        fixture.revokeExchangeAccess()

        fixture.invite()

        assertEquals("PARTICIPANT:PENDING", fixture.bindingState(fixture.groupBindingId()))
        assertEquals(1, fixture.attestations(fixture.groupBindingId()))
    }

    @Test
    fun `a pending primary replaced by a group a request party records keeps that group's binding`() {
        val fixture = fixture()
        val bindingId = fixture.inviteAndAccept()
        val partyId = fixture.assignGroupParty()
        fixture.revokeExchangeAccess()
        fixture.awaitPrimaryAcceptance()

        fixture.replacePrimary(fixture.trust.groupId)

        assertEquals(bindingId, fixture.groupBindingId())
        assertEquals("PRIMARY:PENDING", fixture.bindingState(bindingId))
        assertEquals(1, fixture.attestations(bindingId))
        assertEquals(bindingId, fixture.recipientOf(partyId))
    }

    @Test
    fun `replacing a pending primary that a request party records keeps its binding as a former recipient`() {
        val fixture = fixture()
        val bindingId = fixture.inviteAndAccept()
        val partyId = fixture.assignGroupParty()
        fixture.revokeExchangeAccess()
        fixture.awaitPrimaryAcceptance()
        fixture.replacePrimary(fixture.trust.groupId)
        val replacementGroupId = fixture.publishedGroup()

        fixture.replacePrimary(replacementGroupId)

        assertEquals(bindingId, fixture.recipientOf(partyId))
        assertEquals("PARTICIPANT:PENDING", fixture.bindingState(bindingId))
        assertEquals("REVOKED", fixture.groupShareStatus())
        val primary =
            requireNotNull(QuarkusTransaction.requiringNew().call { recipients.findPrimary(fixture.exchangeId) })
        assertNotEquals(bindingId, primary.id)
        assertEquals(ExchangeRecipientSelectionType.TRUSTED_GROUP, primary.selectionType)
    }

    @Test
    fun `a primary replacement racing an acceptance waits for it and refuses the accepted invitation`() {
        val fixture = fixture()
        fixture.awaitPrimaryAcceptance()
        val primaryId = fixture.primaryBindingId()

        val replacement = fixture.whileAcceptanceCommits(primaryId) {
            runCatching { fixture.replacePrimary(fixture.trust.groupId) }
        }

        assertInstanceOf(IllegalArgumentException::class.java, replacement.exceptionOrNull(), "$replacement")
        assertEquals("PRIMARY:ACCEPTED", fixture.bindingState(primaryId))
    }

    private fun fixture(): ReinvitationFixture {
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
            execute(connection, "UPDATE exchange SET require_recipient_sign_in = TRUE WHERE id = ?", runtime.exchangeId)
            val trust = TrustedGroupSqlFixture(connection, runtime.template.organizationId, runtime.template.userId)
            ReinvitationFixture(runtime, trust).also { runtime.insertUser(it.manager) }
        }
        val owner = QuarkusTransaction.requiringNew().call { appUserService.getById(fixture.ownerUserId) }
        val authTokenContext = mock<AuthTokenContext>()
        whenever(authTokenContext.authToken).thenReturn(AuthToken().apply { appUser = owner })
        whenever(authTokenContext.activeOrganizationId).thenReturn(fixture.trust.ownerOrganizationId)
        QuarkusMock.installMockForType(authTokenContext, AuthTokenContext::class.java)
        QuarkusMock.installMockForType(mock<EmailService>(), EmailService::class.java)
        QuarkusTransaction.requiringNew().run {
            groups.syncMembers(
                fixture.trust.groupId,
                listOf(PrincipalGroupMemberSpec(fixture.manager, groupRole = PrincipalGroupRoleName.MANAGER)),
            )
        }
        return fixture
    }

    private inner class ReinvitationFixture(
        private val runtime: SubmissionRuntimeSqlFixture,
        val trust: TrustedGroupSqlFixture,
    ) {
        val exchangeId: UUID = runtime.exchangeId
        val ownerUserId: UUID = runtime.template.userId
        val manager: UUID = UUID.randomUUID()
        private val ownerAccess = RequestAccessContext(
            PrincipalRef.user(runtime.template.userId),
            AuthorizationContext(sessionRef = "owner-session", mfaSatisfied = true),
        )

        fun invite() {
            accessManagement.inviteTrustedParticipant(
                exchangeId = exchangeId,
                selection = TrustedGroupRecipientSelectionRequest(
                    trust.partnerOrganizationId.toString(),
                    trust.groupId.toString()
                ),
                roleName = ExchangeShareRoleName.VIEWER,
            )
        }

        fun inviteAndAccept(): UUID {
            invite()
            return groupBindingId().also(::accept)
        }

        fun accept(bindingId: UUID) {
            recipients.recordTrustedParticipantDecision(bindingId, manager, true)
        }

        fun assignGroupParty(): UUID = QuarkusTransaction.requiringNew().call {
            partyAssignments.assign(
                AssignInformationRequestPartyCommand(
                    requestId = runtime.requestId,
                    roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                    principal = PrincipalRef.group(trust.groupId),
                    access = ownerAccess,
                    precondition = partiesPrecondition(),
                    idempotencyKey = "assign-group-${UUID.randomUUID()}",
                ),
            ).party.id
        }

        fun revokeParty(partyId: UUID) {
            QuarkusTransaction.requiringNew().run {
                partyRevocations.revoke(
                    RevokeInformationRequestPartyCommand(
                        requestId = runtime.requestId,
                        partyId = partyId,
                        access = ownerAccess,
                        precondition = partiesPrecondition(),
                        idempotencyKey = "revoke-group-${UUID.randomUUID()}",
                    ),
                )
            }
        }

        fun revokeExchangeAccess() {
            accessManagement.revokeAccess(exchangeId, groupShareId())
        }

        fun awaitPrimaryAcceptance() {
            val primaryUser = UUID.randomUUID()
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
                    primaryUser,
                    "primary-recipient-${primaryUser.toString().take(8)}@process.test",
                )
                execute(connection, "UPDATE exchange SET status = 'INITIATED' WHERE id = ?", exchangeId)
            }
            QuarkusTransaction.requiringNew().run {
                val share = shareService.grant(
                    resourceType = ResourceType.EXCHANGE,
                    resourceId = exchangeId,
                    principalKind = PrincipalKind.USER,
                    principalId = primaryUser,
                    roleName = ExchangeShareRoleName.VIEWER,
                    grantedBy = ownerAccess.principal,
                    status = ShareStatus.PENDING_APPROVAL,
                )
                recipients.createBinding(
                    exchangeId = exchangeId,
                    directShare = share,
                    purpose = ExchangeRecipientPurpose.PRIMARY,
                    selectionType = ExchangeRecipientSelectionType.REGISTERED_USER,
                    targetOrganizationId = null,
                    acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING,
                )
            }
        }

        fun replacePrimary(groupId: UUID) {
            accessManagement.replacePrimaryRecipient(
                exchangeId,
                TrustedGroupRecipientSelectionRequest(trust.partnerOrganizationId.toString(), groupId.toString()),
            )
        }

        fun primaryBindingId(): UUID = dataSource.connection.use { connection ->
            UUID.fromString(
                requireNotNull(
                    queryString(
                        connection,
                        "SELECT id::text FROM exchange_recipient WHERE exchange_id = ? AND purpose = 'PRIMARY'",
                        exchangeId,
                    ),
                ),
            )
        }

        fun <T> whileAcceptanceCommits(primaryId: UUID, replace: () -> T): T {
            val locked = CountDownLatch(1)
            val acceptance = CompletableFuture.runAsync {
                dataSource.connection.use { connection ->
                    connection.autoCommit = false
                    queryString(connection, "SELECT CAST(id AS text) FROM exchange WHERE id = ? FOR UPDATE", exchangeId)
                    queryString(
                        connection,
                        "SELECT CAST(id AS text) FROM exchange_recipient WHERE id = ? FOR UPDATE",
                        primaryId
                    )
                    execute(connection, "UPDATE exchange SET status = 'ACCEPTED_STARTED' WHERE id = ?", exchangeId)
                    execute(
                        connection,
                        """
                        UPDATE exchange_recipient
                        SET acceptance_status = 'ACCEPTED', accepted_or_rejected_at = now(),
                            accepted_or_rejected_by_app_user_id =
                                (SELECT s.principal_id FROM share s WHERE s.id = exchange_recipient.direct_share_id)
                        WHERE id = ?
                        """.trimIndent(),
                        primaryId,
                    )
                    locked.countDown()
                    awaitLockWaiter()
                    connection.commit()
                }
            }
            check(locked.await(30, TimeUnit.SECONDS)) { "The acceptance never took its locks" }
            return try {
                replace()
            } finally {
                acceptance.get(60, TimeUnit.SECONDS)
            }
        }

        private fun awaitLockWaiter() {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline) {
                val waiting = dataSource.connection.use { connection ->
                    queryInt(connection, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
                }
                if (waiting > 0) return
                Thread.sleep(25)
            }
        }

        fun publishedGroup(): UUID = UUID.randomUUID().also { groupId ->
            dataSource.connection.use { connection ->
                execute(
                    connection,
                    """
                    INSERT INTO principal_group (id, name, scope, owner_organization_id, externally_published, is_active, created_date)
                    VALUES (?, 'Second published group', 'ORG', ?, TRUE, TRUE, now())
                    """.trimIndent(),
                    groupId,
                    trust.partnerOrganizationId,
                )
            }
        }

        fun groupShareId(): UUID = QuarkusTransaction.requiringNew().call {
            requireNotNull(
                shareService.findDirectForPrincipalOnResource(
                    PrincipalKind.PRINCIPAL_GROUP,
                    trust.groupId,
                    ResourceType.EXCHANGE,
                    exchangeId,
                ),
            ).id
        }

        fun groupShareStatus(): String = dataSource.connection.use { connection ->
            requireNotNull(queryString(connection, "SELECT status FROM share WHERE id = ?", groupShareId()))
        }

        fun groupBindingId(): UUID = dataSource.connection.use { connection ->
            UUID.fromString(
                requireNotNull(
                    queryString(
                        connection,
                        "SELECT id::text FROM exchange_recipient WHERE direct_share_id = ?",
                        groupShareId()
                    ),
                ),
            )
        }

        fun bindingState(bindingId: UUID): String? = dataSource.connection.use { connection ->
            queryString(
                connection,
                "SELECT purpose || ':' || acceptance_status FROM exchange_recipient WHERE id = ?",
                bindingId
            )
        }

        fun attestations(bindingId: UUID): Int = dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT COUNT(*) FROM exchange_recipient_attestation WHERE exchange_recipient_id = ?",
                bindingId
            )
        }

        fun recipientOf(partyId: UUID): UUID? = dataSource.connection.use { connection ->
            queryString(
                connection,
                "SELECT exchange_recipient_id::text FROM information_request_party WHERE id = ?",
                partyId
            )
                ?.let(UUID::fromString)
        }

        fun partyHistory(partyId: UUID): String? = dataSource.connection.use { connection ->
            queryString(
                connection,
                """
                SELECT active || '|' || COALESCE(revoked_at::text, '') || '|' || COALESCE(exchange_recipient_id::text, '')
                       || '|' || COALESCE(share_id::text, '') || '|' || party_revision
                FROM information_request_party WHERE id = ?
                """.trimIndent(),
                partyId,
            )
        }

        private fun partiesPrecondition(): CommandPrecondition =
            CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partiesOf(requireNotNull(requests.findById(runtime.requestId))),
            )
    }
}
