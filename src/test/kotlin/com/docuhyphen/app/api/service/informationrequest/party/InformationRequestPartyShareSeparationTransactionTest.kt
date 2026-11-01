package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
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
class InformationRequestPartyShareSeparationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var partyAssignments: InformationRequestPartyAssignmentService

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var parties: InformationRequestPartyRepository

    @Inject
    lateinit var authorization: AuthorizationService

    @Test
    fun `revoking a member's decision maker party leaves only the contributor party's capabilities`() {
        val fixture = fixture()
        val contributorParty =
            fixture.assign(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-contributor")
        val decisionMakerParty =
            fixture.assign(fixture.memberId, InformationRequestShareRoleKey.DECISION_MAKER, "assign-decision-maker")
        assertTrue(fixture.permits(fixture.memberId, Action.INFORMATION_REQUEST_MANAGE_PARTIES))

        fixture.revoke(decisionMakerParty, "revoke-decision-maker")

        assertTrue(fixture.permits(fixture.memberId, Action.INFORMATION_REQUEST_VIEW))
        assertTrue(!fixture.permits(fixture.memberId, Action.INFORMATION_REQUEST_MANAGE_PARTIES))
        assertTrue(!fixture.permits(fixture.memberId, Action.INFORMATION_REQUEST_EXPORT))
        assertEquals("CONTRIBUTOR:ACTIVE", fixture.shareOf(contributorParty))
        assertEquals("DECISION_MAKER:REVOKED", fixture.shareOf(decisionMakerParty))
    }

    @Test
    fun `a weaker role assigned after a stronger one keeps the stronger party's capabilities`() {
        val fixture = fixture()
        val decisionMakerParty =
            fixture.assign(fixture.memberId, InformationRequestShareRoleKey.DECISION_MAKER, "assign-decision-maker")
        val contributorParty =
            fixture.assign(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-contributor")

        assertTrue(fixture.permits(fixture.memberId, Action.INFORMATION_REQUEST_MANAGE_PARTIES))
        assertEquals("DECISION_MAKER:ACTIVE", fixture.shareOf(decisionMakerParty))
        assertEquals("CONTRIBUTOR:ACTIVE", fixture.shareOf(contributorParty))
    }

    @Test
    fun `reassigning one of a member's parties moves only that party's capabilities to the new holder`() {
        val fixture = fixture()
        fixture.assign(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-contributor")
        val decisionMakerParty =
            fixture.assign(fixture.memberId, InformationRequestShareRoleKey.DECISION_MAKER, "assign-decision-maker")
        val replacementId = fixture.user()

        QuarkusTransaction.requiringNew().run {
            partyRevocations.reassign(
                ReassignInformationRequestPartyCommand(
                    requestId = fixture.requestId,
                    partyId = decisionMakerParty,
                    principal = PrincipalRef.user(replacementId),
                    access = fixture.manager,
                    precondition = fixture.partiesPrecondition(),
                    idempotencyKey = "reassign-decision-maker",
                ),
            )
        }

        assertTrue(!fixture.permits(fixture.memberId, Action.INFORMATION_REQUEST_MANAGE_PARTIES))
        assertTrue(fixture.permits(fixture.memberId, Action.INFORMATION_REQUEST_VIEW))
        assertTrue(fixture.permits(replacementId, Action.INFORMATION_REQUEST_MANAGE_PARTIES))
    }

    @Test
    fun `a principal assigned again gets a new Share that resolves to the new party`() {
        val fixture = fixture()
        val first = fixture.assign(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-first")
        fixture.revoke(first, "revoke-first")
        val second = fixture.assign(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-second")

        val firstShare = fixture.shareIdOf(first)
        val secondShare = fixture.shareIdOf(second)

        assertNotEquals(firstShare, secondShare)
        assertEquals(second, QuarkusTransaction.requiringNew().call { parties.findByShareId(secondShare) }?.id)
        assertEquals(1, fixture.activeShares(fixture.memberId))
    }

    @Test
    fun `a principal holds one active party per role but keeps distinct roles and assignment after revocation`() {
        val fixture = fixture()
        val first = fixture.assign(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-contributor")
        val reserved = fixture.capacityReservations()

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.assign(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-contributor-again")
        }

        assertEquals(InformationRequestErrorCatalog.PARTY_ALREADY_ASSIGNED, refusal.reasonCode)
        assertEquals(1, fixture.activeParties(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR))
        assertEquals(reserved, fixture.capacityReservations())
        assertEquals(1, fixture.activeShares(fixture.memberId))
        fixture.assign(fixture.memberId, InformationRequestShareRoleKey.DECISION_MAKER, "assign-decision-maker")
        fixture.revoke(first, "revoke-contributor")
        fixture.assign(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-after-revocation")
        assertEquals(1, fixture.activeParties(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR))
        assertEquals(1, fixture.activeParties(fixture.memberId, InformationRequestShareRoleKey.DECISION_MAKER))
    }

    @Test
    fun `reassigning a party to a principal that already holds its role is refused before access changes`() {
        val fixture = fixture()
        fixture.assign(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-member")
        val otherId = fixture.user()
        val otherParty = fixture.assign(otherId, InformationRequestShareRoleKey.CONTRIBUTOR, "assign-other")

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().run {
                partyRevocations.reassign(
                    ReassignInformationRequestPartyCommand(
                        requestId = fixture.requestId,
                        partyId = otherParty,
                        principal = PrincipalRef.user(fixture.memberId),
                        access = fixture.manager,
                        precondition = fixture.partiesPrecondition(),
                        idempotencyKey = "reassign-onto-member",
                    ),
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.PARTY_ALREADY_ASSIGNED, refusal.reasonCode)
        assertEquals("CONTRIBUTOR:ACTIVE", fixture.shareOf(otherParty))
        assertEquals(1, fixture.activeParties(otherId, InformationRequestShareRoleKey.CONTRIBUTOR))
        assertEquals(1, fixture.activeParties(fixture.memberId, InformationRequestShareRoleKey.CONTRIBUTOR))
    }

    private fun fixture(): ShareFixture = dataSource.connection.use { connection ->
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
        val memberId = UUID.randomUUID()
        runtime.insertUser(memberId)
        ShareFixture(runtime, memberId)
    }

    private inner class ShareFixture(private val runtime: SubmissionRuntimeSqlFixture, val memberId: UUID) {
        val requestId: UUID = runtime.requestId
        val manager = RequestAccessContext(
            PrincipalRef.user(runtime.template.userId),
            AuthorizationContext(sessionRef = "manager-session")
        )

        fun assign(userId: UUID, role: InformationRequestShareRoleKey, key: String): UUID =
            QuarkusTransaction.requiringNew().call {
                partyAssignments.assign(
                    AssignInformationRequestPartyCommand(
                        requestId = requestId,
                        roleKey = role,
                        principal = PrincipalRef.user(userId),
                        access = manager,
                        precondition = partiesPrecondition(),
                        idempotencyKey = key,
                    ),
                ).party.id
            }

        fun revoke(partyId: UUID, key: String) {
            QuarkusTransaction.requiringNew().run {
                partyRevocations.revoke(
                    RevokeInformationRequestPartyCommand(
                        requestId = requestId,
                        partyId = partyId,
                        access = manager,
                        precondition = partiesPrecondition(),
                        idempotencyKey = key,
                    ),
                )
            }
        }

        fun partiesPrecondition(): CommandPrecondition = QuarkusTransaction.requiringNew().call {
            CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partiesOf(
                    requireNotNull(
                        requests.findById(
                            requestId
                        )
                    )
                )
            )
        }

        fun permits(userId: UUID, action: Action): Boolean = QuarkusTransaction.requiringNew().call {
            authorization.authorize(
                PrincipalRef.user(userId),
                action,
                ResourceRef.informationRequest(requestId),
                AuthorizationContext(sessionRef = "member-session", mfaSatisfied = true),
            ) is Decision.Allow
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
                    "holder-${id.toString().take(8)}@process.test",
                )
            }
        }

        fun shareOf(partyId: UUID): String? = dataSource.connection.use {
            queryString(
                it,
                """
                SELECT share.role_name || ':' || share.status
                FROM share JOIN information_request_party party ON party.share_id = share.id
                WHERE party.id = ?
                """.trimIndent(),
                partyId,
            )
        }

        fun shareIdOf(partyId: UUID): UUID = dataSource.connection.use {
            UUID.fromString(
                requireNotNull(
                    queryString(
                        it,
                        "SELECT share_id::text FROM information_request_party WHERE id = ?",
                        partyId
                    )
                )
            )
        }

        fun activeParties(userId: UUID, role: InformationRequestShareRoleKey): Int = dataSource.connection.use {
            queryInt(
                it,
                "SELECT COUNT(*) FROM information_request_party WHERE information_request_id = ? AND principal_id = ? AND role_key = ? AND active",
                requestId,
                userId,
                role.name,
            )
        }

        fun capacityReservations(): Int = dataSource.connection.use {
            queryInt(
                it,
                """
                SELECT COUNT(*)
                FROM request_execution_usage_reservation reservation
                         JOIN request_execution_grant execution_grant ON execution_grant.id = reservation.grant_id
                WHERE execution_grant.request_id = ?
                """.trimIndent(),
                requestId,
            )
        }

        fun activeShares(userId: UUID): Int = dataSource.connection.use {
            queryInt(
                it,
                "SELECT COUNT(*) FROM share WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND principal_id = ? AND status = 'ACTIVE'",
                requestId,
                userId,
            )
        }
    }
}
