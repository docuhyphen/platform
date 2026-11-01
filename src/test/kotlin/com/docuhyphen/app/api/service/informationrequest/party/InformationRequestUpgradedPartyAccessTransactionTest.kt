package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.noauth.UpgradeInformationRequestParticipantAccountCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewPlan
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewSnapshot
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStagePlan
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.RevisionETag
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestContactProofService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestParticipantAccountUpgradeService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewSeparationPolicy
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestUpgradedPartyAccessTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var partyAssignments: InformationRequestPartyAssignmentService

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var upgrades: InformationRequestParticipantAccountUpgradeService

    @Inject
    lateinit var requests: InformationRequestRepository

    @Inject
    lateinit var authorization: AuthorizationService

    @Inject
    lateinit var separation: InformationRequestReviewSeparationPolicy

    @Inject
    lateinit var contactProof: InformationRequestContactProofService

    @Inject
    lateinit var requirementContexts: InformationRequestRequirementAuthorizationContextProvider

    @Inject
    lateinit var parentLifecycle: InformationRequestParentLifecycleService

    @Test
    fun `linking an account moves every party the participant holds on the request and ends all of their access links`() {
        val fixture = fixture()
        val (attestorPartyId, attestorLinkId, attestorSessionId) = fixture.secondParty("ATTESTOR")

        upgrades.upgrade(fixture.upgradeCommand())

        val linkedShares = listOf(fixture.partyId, attestorPartyId).map { requireNotNull(fixture.linkedShareOf(it)) }
        assertEquals(listOf("CONTRIBUTOR", "ATTESTOR"), linkedShares.map { fixture.shareRole(UUID.fromString(it)) })
        assertEquals(listOf("ACTIVE", "ACTIVE"), linkedShares.map { fixture.shareStatus(UUID.fromString(it)) })
        assertEquals("REVOKED", fixture.linkStatusOf(attestorLinkId))
        assertTrue(fixture.sessionRevokedOf(attestorSessionId))
    }

    @Test
    fun `after linking, the participant no longer acts for the party it linked while the account does`() {
        val fixture = fixture()

        upgrades.upgrade(fixture.upgradeCommand())

        assertEquals(emptyList<UUID>(), fixture.actingFor(PrincipalRef.participant(fixture.participantId)))
        assertEquals(listOf(fixture.partyId), fixture.actingFor(PrincipalRef.user(fixture.appUserId)))
    }

    @Test
    fun `after linking, the original access link is refused as linked to an account rather than as revoked`() {
        val fixture = fixture()

        upgrades.upgrade(fixture.upgradeCommand())

        val proof = assertThrows(InformationRequestLifecycleException::class.java) {
            contactProof.issueChallenge(fixture.rawToken)
        }
        val read = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().run { contactProof.resolveBootstrapLink(fixture.rawToken) }
        }
        assertEquals(
            listOf(
                InformationRequestErrorCatalog.ACCESS_LINK_PARTY_ACCOUNT_LINKED,
                InformationRequestErrorCatalog.ACCESS_LINK_PARTY_ACCOUNT_LINKED,
            ),
            listOf(proof.reasonCode, read.reasonCode),
        )
    }

    @Test
    fun `an upgrade is refused while an earlier Share of the account is live although its newest one on the request has ended`() {
        val fixture = fixture()
        fixture.accountShare("REVIEWER", "ACTIVE", minutesAgo = 60)
        fixture.accountShare("ATTESTOR", "REVOKED", minutesAgo = 0)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            upgrades.upgrade(fixture.upgradeCommand())
        }

        assertEquals(InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_ACCESS_CONFLICT, refusal.reasonCode)
        assertEquals(0, fixture.accountLinkCount())
        assertEquals("ACTIVE", fixture.linkStatus())
        assertNull(fixture.linkedShare())
    }

    @Test
    fun `an upgrade is refused when the owner's sharing rules do not admit the account's organization`() {
        val fixture = fixture()
        fixture.joinUntrustedOrganization()

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            upgrades.upgrade(fixture.upgradeCommand())
        }

        assertEquals(InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED, refusal.reasonCode)
        assertEquals(0, fixture.accountLinkCount())
        assertNull(fixture.linkedShare())
        assertFalse(fixture.accountMayView())
    }

    @Test
    fun `once the Exchange requires recipients to sign in, a participant who never linked cannot link an account or gain access`() {
        val fixture = fixture()
        fixture.requireRecipientSignIn()

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            upgrades.upgrade(fixture.upgradeCommand())
        }

        assertEquals(InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED, refusal.reasonCode)
        assertEquals(0, fixture.accountLinkCount())
        assertNull(fixture.linkedShare())
        assertFalse(fixture.accountMayView())
    }

    @Test
    fun `an upgrade is refused while the request's owner is operationally suspended`() {
        val fixture = fixture()
        fixture.suspendOwner()

        val refusal = assertThrows(SubscriptionDenialException::class.java) {
            upgrades.upgrade(fixture.upgradeCommand())
        }

        assertEquals(SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED, refusal.denial.reason)
        assertEquals(0, fixture.accountLinkCount())
        assertEquals("ACTIVE", fixture.linkStatus())
        assertNull(fixture.linkedShare())
        assertFalse(fixture.accountMayView())
    }

    @Test
    fun `revoking an upgraded party ends the registered account's access to the request`() {
        val fixture = fixture()
        val accountShareId = upgrades.upgrade(fixture.upgradeCommand()).grantedShare.id
        assertTrue(fixture.accountMayView())

        partyRevocations.revoke(fixture.revoke(fixture.partyId, "revoke-upgraded-party"))

        assertEquals("REVOKED", fixture.shareStatus(accountShareId))
        assertEquals("REVOKED", fixture.shareStatus(fixture.participantShareId))
        assertFalse(fixture.accountMayView())
    }

    @Test
    fun `reassigning an upgraded party ends the registered account's access and clears its link`() {
        val fixture = fixture()
        fixture.grantExecution()
        val accountShareId = upgrades.upgrade(fixture.upgradeCommand()).grantedShare.id
        assertEquals(accountShareId.toString(), fixture.linkedShare())

        partyRevocations.reassign(
            ReassignInformationRequestPartyCommand(
                requestId = fixture.requestId,
                partyId = fixture.partyId,
                principal = PrincipalRef.user(fixture.user()),
                access = fixture.managerAccess(),
                precondition = fixture.partiesPrecondition(),
                idempotencyKey = "reassign-upgraded-party",
            ),
        )

        assertEquals("REVOKED", fixture.shareStatus(accountShareId))
        assertNull(fixture.linkedShare())
        assertFalse(fixture.accountMayView())
    }

    @Test
    fun `revoking the upgraded party ends only its own account Share while the account's other party keeps its access`() {
        val fixture = fixture()
        fixture.grantExecution()
        val accountShareId = upgrades.upgrade(fixture.upgradeCommand()).grantedShare.id
        val reviewerParty = partyAssignments.assign(fixture.assignAccount("assign-account-reviewer")).party
        val reviewerShareId = requireNotNull(reviewerParty.shareId)
        assertNotEquals(accountShareId, reviewerShareId)

        partyRevocations.revoke(fixture.revoke(fixture.partyId, "revoke-upgraded-party"))

        assertEquals("REVOKED", fixture.shareStatus(accountShareId))
        assertEquals("REVIEWER", fixture.shareRole(reviewerShareId))
        assertTrue(fixture.accountMayView())

        partyRevocations.revoke(fixture.revoke(reviewerParty.id, "revoke-account-reviewer"))

        assertEquals("REVOKED", fixture.shareStatus(reviewerShareId))
        assertFalse(fixture.accountMayView())
    }

    @Test
    fun `an upgrade is refused while the registered account already holds access to the request`() {
        val fixture = fixture()
        fixture.grantExecution()
        val accountShareId =
            requireNotNull(partyAssignments.assign(fixture.assignAccount("assign-account-first")).party.shareId)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            upgrades.upgrade(fixture.upgradeCommand())
        }

        assertEquals(InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_ACCESS_CONFLICT, refusal.reasonCode)
        assertEquals("REVIEWER", fixture.shareRole(accountShareId))
        assertEquals(0, fixture.accountLinkCount())
        assertEquals("ACTIVE", fixture.linkStatus())
        assertFalse(fixture.sessionRevoked())
        assertNull(fixture.linkedShare())
    }

    @Test
    fun `an account linked to the submitting participant is excluded from independent review after the participant's party is reassigned`() {
        val fixture = fixture()
        fixture.grantExecution()
        upgrades.upgrade(fixture.upgradeCommand())
        val submitted = submittedByLinkedParticipant(fixture)
        partyAssignments.assign(fixture.assignAccount("assign-linked-account-reviewer"))
        partyRevocations.reassign(
            ReassignInformationRequestPartyCommand(
                requestId = fixture.requestId,
                partyId = fixture.partyId,
                principal = PrincipalRef.user(fixture.user()),
                access = fixture.managerAccess(),
                precondition = fixture.partiesPrecondition(),
                idempotencyKey = "reassign-linked-respondent",
            ),
        )

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().run {
                separation.requireMayReview(submitted, INDEPENDENT_STAGE, setOf(PrincipalRef.user(fixture.appUserId)))
            }
        }

        assertEquals(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, refusal.reasonCode)
        QuarkusTransaction.requiringNew().run {
            separation.requireMayReview(submitted, INDEPENDENT_STAGE, setOf(PrincipalRef.user(fixture.user())))
        }
    }

    @Test
    fun `an account linked to the submitting participant is excluded while the participant's party still names it`() {
        val fixture = fixture()
        fixture.grantExecution()
        upgrades.upgrade(fixture.upgradeCommand())
        val submitted = submittedByLinkedParticipant(fixture)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().run {
                separation.requireMayReview(submitted, INDEPENDENT_STAGE, setOf(PrincipalRef.user(fixture.appUserId)))
            }
        }

        assertEquals(InformationRequestErrorCatalog.REVIEW_SEPARATION_OF_DUTIES, refusal.reasonCode)
    }

    private fun submittedByLinkedParticipant(fixture: UpgradedPartyFixture): InformationRequestReviewSnapshot {
        val participantId = dataSource.connection.use {
            UUID.fromString(
                requireNotNull(
                    queryString(
                        it,
                        "SELECT participant_id::text FROM participant_account_link WHERE app_user_id = ?",
                        fixture.appUserId,
                    ),
                ),
            )
        }
        val submitted = InformationRequestSubmissionPackage().apply {
            informationRequestId = fixture.requestId
            templateVersionId = UUID.randomUUID()
            submittedByPrincipalKind = PrincipalKind.PARTICIPANT
            submittedByPrincipalId = participantId
        }
        return InformationRequestReviewSnapshot(
            review = InformationRequestReview().apply {
                informationRequestId = fixture.requestId
                packageId = submitted.id
                templateVersionId = submitted.templateVersionId
            },
            submission = InformationRequestSubmissionPackageView(
                submitted,
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                null,
            ),
            plan = InformationRequestReviewPlan(
                submitted.templateVersionId,
                InformationRequestReviewStageOrdering.SEQUENTIAL,
                listOf(INDEPENDENT_STAGE),
            ),
            reviewedItems = emptyList(),
            coverage = emptyMap(),
            assignments = emptyList(),
            decisions = emptyList(),
            findings = emptyList(),
            standing = InformationRequestReviewResult(emptyList(), null, emptyMap()),
        )
    }

    private fun fixture(): UpgradedPartyFixture = dataSource.connection.use { connection ->
        val runtime = SubmissionRuntimeSqlFixture(connection)
        execute(
            connection, """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
        """.trimIndent(), UUID.randomUUID(), runtime.exchangeId, runtime.template.userId
        )
        val participantId = UUID.randomUUID()
        val appUserId = UUID.randomUUID()
        val email = "upgraded-${participantId.toString().take(8)}@process.test"
        execute(
            connection, """
            INSERT INTO external_participant (id, owner_organization_id, email, email_lower, is_active, created_date, email_verified_at)
            VALUES (?, ?, ?, ?, TRUE, now(), now())
        """.trimIndent(), participantId, runtime.template.organizationId, email, email
        )
        insertUser(connection, appUserId, email)
        val participantShareId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'PARTICIPANT', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
        """.trimIndent(), participantShareId, runtime.requestId, participantId
        )
        val partyId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'CONTRIBUTOR', 'PARTICIPANT', ?, ?, TRUE, 1, now(), now(), now())
        """.trimIndent(), partyId, runtime.requestId, participantId, participantShareId
        )
        val linkId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO share_link
                (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                 contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength)
            VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 1, FALSE, 0, 0, 0, 5, now() + interval '30 days', now(), 'EMAIL_OTP')
        """.trimIndent(), linkId, participantShareId, CommandRequestFingerprint.sha256Hex("upgraded-token-$linkId")
        )
        val sessionId = UUID.randomUUID()
        val secret =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        execute(
            connection, """
            INSERT INTO request_access_session
                (id, share_link_id, participant_principal_kind, participant_principal_id, verification_strength,
                 issued_at, expires_at, credential_hash, created_at)
            VALUES (?, ?, 'PARTICIPANT', ?, 'EMAIL_OTP', now(), now() + interval '1 hour', ?, now())
        """.trimIndent(), sessionId, linkId, participantId, CommandRequestFingerprint.sha256Hex(secret)
        )
        UpgradedPartyFixture(
            runtime,
            participantShareId,
            partyId,
            appUserId,
            linkId,
            sessionId,
            "$sessionId.$secret",
            participantId,
        )
    }

    private fun insertUser(connection: java.sql.Connection, id: UUID, email: String) {
        execute(
            connection, """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
        """.trimIndent(), id, email
        )
    }

    private inner class UpgradedPartyFixture(
        private val runtime: SubmissionRuntimeSqlFixture,
        val participantShareId: UUID,
        val partyId: UUID,
        val appUserId: UUID,
        private val linkId: UUID,
        private val sessionId: UUID,
        private val sessionToken: String,
        val participantId: UUID,
    ) {
        val requestId: UUID = runtime.requestId
        val rawToken = "upgraded-token-$linkId"

        fun upgradeCommand() = UpgradeInformationRequestParticipantAccountCommand(
            requestId = requestId,
            sessionId = sessionId,
            sessionCredential = sessionToken,
            access = RequestAccessContext(PrincipalRef.user(appUserId), AuthorizationContext()),
            precondition = CommandPrecondition.ExpectedRevision(RevisionETag.of(partyId, 1L)),
            idempotencyKey = "upgrade-$partyId",
        )

        fun managerAccess() = RequestAccessContext(PrincipalRef.user(runtime.template.userId), AuthorizationContext())

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

        fun revoke(partyId: UUID, key: String) = RevokeInformationRequestPartyCommand(
            requestId = requestId,
            partyId = partyId,
            access = managerAccess(),
            precondition = partiesPrecondition(),
            idempotencyKey = key,
        )

        fun assignAccount(key: String) = AssignInformationRequestPartyCommand(
            requestId = requestId,
            roleKey = InformationRequestShareRoleKey.REVIEWER,
            principal = PrincipalRef.user(appUserId),
            access = managerAccess(),
            precondition = partiesPrecondition(),
            idempotencyKey = key,
        )

        fun user(): UUID = UUID.randomUUID().also { id ->
            dataSource.connection.use { insertUser(it, id, "replacement-${id.toString().take(8)}@process.test") }
        }

        fun grantExecution() {
            dataSource.connection.use { connection ->
                execute(
                    connection, """
                    INSERT INTO request_execution_grant
                        (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                         enforcement_mode, acting_party_cap, issued_at, created_at)
                    VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, now(), now())
                """.trimIndent(), UUID.randomUUID(), requestId, runtime.template.organizationId
                )
            }
        }

        fun accountMayView(): Boolean = QuarkusTransaction.requiringNew().call {
            authorization.authorize(
                PrincipalRef.user(appUserId),
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                AuthorizationContext(),
            ) !is Decision.Deny
        }

        fun shareStatus(shareId: UUID): String? =
            dataSource.connection.use { queryString(it, "SELECT status FROM share WHERE id = ?", shareId) }

        fun shareRole(shareId: UUID): String? =
            dataSource.connection.use { queryString(it, "SELECT role_name FROM share WHERE id = ?", shareId) }

        fun linkedShare(): String? = dataSource.connection.use {
            queryString(it, "SELECT linked_user_share_id::text FROM information_request_party WHERE id = ?", partyId)
        }

        fun linkStatus(): String? =
            dataSource.connection.use { queryString(it, "SELECT status FROM share_link WHERE id = ?", linkId) }

        fun sessionRevoked(): Boolean = dataSource.connection.use {
            queryString(it, "SELECT revoked_at::text FROM request_access_session WHERE id = ?", sessionId) != null
        }

        fun accountLinkCount(): Int = dataSource.connection.use {
            queryInt(it, "SELECT count(*) FROM participant_account_link WHERE app_user_id = ?", appUserId)
        }

        fun linkedShareOf(partyId: UUID): String? = dataSource.connection.use {
            queryString(it, "SELECT linked_user_share_id::text FROM information_request_party WHERE id = ?", partyId)
        }

        fun linkStatusOf(linkId: UUID): String? =
            dataSource.connection.use { queryString(it, "SELECT status FROM share_link WHERE id = ?", linkId) }

        fun sessionRevokedOf(sessionId: UUID): Boolean = dataSource.connection.use {
            queryString(it, "SELECT revoked_at::text FROM request_access_session WHERE id = ?", sessionId) != null
        }

        fun secondParty(role: String): Triple<UUID, UUID, UUID> {
            val shareId = UUID.randomUUID()
            val partyId = UUID.randomUUID()
            val linkId = UUID.randomUUID()
            val sessionId = UUID.randomUUID()
            dataSource.connection.use { connection ->
                execute(
                    connection, """
                    INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                    VALUES (?, 'INFORMATION_REQUEST', ?, 'PARTICIPANT', ?, ?, 'DIRECT', 'ACTIVE', now())
                """.trimIndent(), shareId, requestId, participantId, role
                )
                execute(
                    connection, """
                    INSERT INTO information_request_party
                        (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                         assigned_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'PARTICIPANT', ?, ?, TRUE, 1, now(), now(), now())
                """.trimIndent(), partyId, requestId, role, participantId, shareId
                )
                execute(
                    connection, """
                    INSERT INTO share_link
                        (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                         contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength)
                    VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 1, FALSE, 0, 0, 0, 5, now() + interval '30 days', now(), 'EMAIL_OTP')
                """.trimIndent(), linkId, shareId, CommandRequestFingerprint.sha256Hex("second-token-$linkId")
                )
                execute(
                    connection,
                    """
                    INSERT INTO request_access_session
                        (id, share_link_id, participant_principal_kind, participant_principal_id, verification_strength,
                         issued_at, expires_at, credential_hash, created_at)
                    VALUES (?, ?, 'PARTICIPANT', ?, 'EMAIL_OTP', now(), now() + interval '1 hour', ?, now())
                """.trimIndent(),
                    sessionId,
                    linkId,
                    participantId,
                    CommandRequestFingerprint.sha256Hex(sessionId.toString())
                )
            }
            return Triple(partyId, linkId, sessionId)
        }

        fun accountShare(role: String, status: String, minutesAgo: Int): UUID = UUID.randomUUID().also { id ->
            dataSource.connection.use { connection ->
                execute(
                    connection, """
                    INSERT INTO share
                        (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at, revoked_at)
                    VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', ?, now() - make_interval(mins => ?),
                            CASE WHEN ? = 'REVOKED' THEN now() END)
                """.trimIndent(), id, requestId, appUserId, role, status, minutesAgo, status
                )
            }
        }

        fun joinUntrustedOrganization() {
            val organizationId = UUID.randomUUID()
            dataSource.connection.use { connection ->
                execute(
                    connection, """
                    INSERT INTO organization (id, name, registration_number, is_active, verification_complete, created_date)
                    VALUES (?, 'Unrelated Organization', ?, TRUE, TRUE, now())
                """.trimIndent(), organizationId, "REG-${organizationId.toString().take(8)}"
                )
                execute(
                    connection, """
                    INSERT INTO organization_membership (id, app_user_id, organization_id, status, is_primary, joined_at, created_date)
                    VALUES (?, ?, ?, 'ACTIVE', TRUE, now(), now())
                """.trimIndent(), UUID.randomUUID(), appUserId, organizationId
                )
            }
        }

        fun suspendOwner() {
            dataSource.connection.use { connection ->
                execute(
                    connection,
                    "DELETE FROM organization_subscription_policy WHERE organization_id = ?",
                    runtime.template.organizationId,
                )
                execute(
                    connection, """
                    INSERT INTO organization_subscription_policy
                        (id, organization_id, tier_code, subscription_status, max_users, current_period_end, created_date, updated_date)
                    VALUES (?, ?, 'BUSINESS', 'SUSPENDED', 5, now() + interval '30 days', now(), now())
                """.trimIndent(), UUID.randomUUID(), runtime.template.organizationId
                )
            }
        }

        fun requireRecipientSignIn() {
            dataSource.connection.use {
                execute(it, "UPDATE exchange SET require_recipient_sign_in = TRUE WHERE id = ?", runtime.exchangeId)
            }
            QuarkusTransaction.requiringNew().run {
                parentLifecycle.enforceRecipientSignIn(
                    runtime.exchangeId,
                    InformationRequestAuditActor.of(managerAccess())
                )
            }
        }

        fun actingFor(principal: PrincipalRef): List<UUID> = QuarkusTransaction.requiringNew().call {
            requirementContexts.actingPartiesFor(
                requireNotNull(requests.findById(requestId)),
                setOf(InformationRequestContributorRole.CONTRIBUTOR),
                principal,
                runtime.documentRequirementId,
            ).map { it.party.id }
        }
    }

    private companion object {
        val INDEPENDENT_STAGE = InformationRequestReviewStagePlan(
            id = UUID.randomUUID(),
            stageKey = "independent-review",
            position = 1,
            title = "Independent review",
            aggregation = InformationRequestReviewAggregation.ANY,
            quorumCount = null,
            minimumReviewerCount = 1,
            tieResolution = InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
            overridePermitted = false,
            excludesResponseParties = true,
            excludesPriorReviewers = false,
            coveredSectionIds = emptySet(),
        )
    }
}
