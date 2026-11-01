package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestParticipantAccountUpgradeDto
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.noauth.UpgradeInformationRequestParticipantAccountCommand
import com.docuhyphen.app.api.resource.informationrequest.noauth.InformationRequestParticipantAccountLinkResource
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpgradeInformationRequestParticipantAccountRequest
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.OverlappingCommandRace
import com.docuhyphen.app.api.service.command.RevisionETag
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.SecureRandom
import java.sql.SQLException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestParticipantAccountUpgradeRecordingTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var upgrades: InformationRequestParticipantAccountUpgradeService

    @Test
    fun `an upgrade records the account link and every link it cuts off under the registering account and its session`() {
        val fixture = fixture()
        val command = fixture.command("upgrade-records")

        val upgrade = upgrades.upgrade(command)
        val replay = upgrades.upgrade(command)

        assertEquals(upgrade.participantAccountLink.id, replay.participantAccountLink.id)
        assertEquals(upgrade.partyETag, replay.partyETag)

        assertEquals(
            listOf(
                "REVOKE_ACCESS_LINK|PARTICIPANT_ACCOUNT_LINKED",
                "REVOKE_ACCESS_LINK|PARTICIPANT_ACCOUNT_LINKED",
                "LINK_PARTICIPANT_ACCOUNT|"
            ),
            fixture.history(),
        )
        fixture.assertRecords("information_request.access_link.revoke", 2)
        fixture.assertRecords("information_request.participant_account.link", 1)
        assertEquals(
            setOf(upgrade.participantAccountLink.id.toString()),
            fixture.payloadValues("information_request.participant_account.link", "participantAccountLinkId")
        )
        assertEquals(
            fixture.linkIds.map(UUID::toString).toSet(),
            fixture.payloadValues("information_request.access_link.revoke", "shareLinkId")
        )
        assertEquals(listOf("REVOKED", "REVOKED"), fixture.linkIds.map(fixture::linkStatus))
        assertTrue(fixture.sessionRevoked())
    }

    @Test
    fun `parallel upgrades under one command key link the account exactly once and every retry returns that link`() {
        val fixture = fixture()
        val command = fixture.command("upgrade-parallel")

        val results = inParallel(4) { upgrades.upgrade(command) }

        assertTrue(results.all { it.isSuccess }, results.toString())
        assertEquals(1, results.map { it.getOrThrow().participantAccountLink.id }.toSet().size)
        assertEquals(1, fixture.accountLinkCount())
    }

    @Test
    fun `an overlapping retry of an upgrade waits for the first and returns the same link`() {
        val fixture = fixture()
        val command = fixture.command("upgrade-overlapping")

        val outcome = OverlappingCommandRace(dataSource)
            .whileRowLocked("exchange", fixture.exchangeId, List(2) { { upgrades.upgrade(command) } })

        assertTrue(outcome.overlapped, "both retries wait before linking the account")
        assertTrue(outcome.results.all { it.isSuccess }, outcome.results.toString())
        assertEquals(1, outcome.results.map { it.getOrThrow().participantAccountLink.id }.toSet().size)
        assertEquals(1, outcome.results.map { it.getOrThrow().partyETag }.toSet().size)
        assertEquals(1, fixture.accountLinkCount())
        assertEquals(
            listOf(
                "REVOKE_ACCESS_LINK|PARTICIPANT_ACCOUNT_LINKED",
                "REVOKE_ACCESS_LINK|PARTICIPANT_ACCOUNT_LINKED",
                "LINK_PARTICIPANT_ACCOUNT|",
            ),
            fixture.history(),
        )
    }

    @Test
    fun `an upgrade waits for the request's parent Exchange before it locks its access session`() {
        val fixture = fixture()
        val command = fixture.command("upgrade-lock-order")
        val race = OverlappingCommandRace(dataSource)
        val pool = Executors.newSingleThreadExecutor()
        try {
            dataSource.connection.use { holder ->
                holder.autoCommit = false
                queryString(holder, "SELECT CAST(id AS text) FROM exchange WHERE id = ? FOR UPDATE", fixture.exchangeId)
                val pending = CompletableFuture.supplyAsync(
                    { runCatching { race.inNewTransaction { upgrades.upgrade(command) } } },
                    pool,
                )
                val waited = race.awaitLockWaiters(1, listOf(pending))
                val sessionFree = fixture.sessionLockAvailable()
                holder.commit()

                assertTrue(waited, "the upgrade waits for the parent Exchange")
                assertTrue(sessionFree, "the waiting upgrade holds no lock on its access session")
                assertTrue(pending.get(60, TimeUnit.SECONDS).isSuccess)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `an upgrade that cannot be recorded leaves no account link, granted Share, or credential change`() {
        val fixture = fixture()
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenAnswer { invocation ->
            val draft = invocation.getArgument<AuditEventDraft>(0)
            if (draft.eventTypeKey == "information_request.participant_account.link") AuditCaptureResult.Degraded(
                draft.eventId,
                "Audit outbox unavailable"
            )
            else AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
        }
        QuarkusMock.installMockForType(recorder, AuditRecorder::class.java)

        val refusal = assertThrows(Throwable::class.java) { upgrades.upgrade(fixture.command("upgrade-unrecorded")) }

        assertTrue(generateSequence(refusal) { it.cause }.any { it is AuditCaptureFailedException })
        assertTrue(fixture.history().isEmpty())
        assertEquals(0, fixture.accountLinkCount())
        assertEquals(0, fixture.userShareCount())
        assertEquals(listOf("ACTIVE", "ACTIVE"), fixture.linkIds.map(fixture::linkStatus))
        assertFalse(fixture.sessionRevoked())
    }

    @Test
    fun `a valid account link succeeds through the adapter without a caller transaction`() {
        val fixture = fixture()

        val response = fixture.linkThroughAdapter("adapter-link")

        assertEquals(201, response.status, response.entity.toString())
        assertEquals(1, fixture.accountLinkCount())
        assertEquals(1, fixture.userShareCount())
        assertTrue(fixture.sessionRevoked())
    }

    @Test
    fun `a retry through the adapter after the link cut off its own session returns the recorded link`() {
        val fixture = fixture()

        val first = fixture.linkThroughAdapter("adapter-retry")
        val retry = fixture.linkThroughAdapter("adapter-retry")

        assertEquals(201, first.status, first.entity.toString())
        assertTrue(fixture.sessionRevoked())
        assertEquals(201, retry.status, retry.entity.toString())
        assertEquals(linkIdOf(first), linkIdOf(retry))
        assertEquals(first.getHeaderString("ETag"), retry.getHeaderString("ETag"))
        assertEquals(1, fixture.accountLinkCount())
        assertEquals(1, fixture.userShareCount())
        assertEquals(3, fixture.history().size)
    }

    @Test
    fun `a session cut off by its account link cannot start another account link`() {
        val fixture = fixture()
        fixture.linkThroughAdapter("adapter-first")

        val another = fixture.linkThroughAdapter("adapter-another")

        assertEquals(409, another.status)
        assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED, reasonCodeOf(another))
        assertEquals(1, fixture.accountLinkCount())
        assertEquals(3, fixture.history().size)
    }

    @Test
    fun `replaying a recorded account link needs the session credential that recorded it`() {
        val fixture = fixture()
        fixture.linkThroughAdapter("adapter-credential")

        val forged = fixture.linkThroughAdapter(
            "adapter-credential",
            sessionToken = "${fixture.sessionId}.${"A".repeat(43)}",
        )

        assertEquals(409, forged.status)
        assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED, reasonCodeOf(forged))
    }

    @Test
    fun `replaying a recorded account link for another signed-in account is refused`() {
        val fixture = fixture()
        fixture.linkThroughAdapter("adapter-account")

        val other = fixture.linkThroughAdapter("adapter-account", accountId = UUID.randomUUID())

        assertEquals(409, other.status)
        assertEquals("COMMAND_RECEIPT_FINGERPRINT_CONFLICT", reasonCodeOf(other))
        assertEquals(1, fixture.accountLinkCount())
    }

    @Test
    fun `a session credential for another session is refused before any account link`() {
        val fixture = fixture()

        val response = fixture.linkThroughAdapter("adapter-named-session", namedSessionId = UUID.randomUUID())

        assertEquals(403, response.status)
        assertEquals(0, fixture.accountLinkCount())
        assertFalse(fixture.sessionRevoked())
    }

    private fun linkIdOf(response: Response): UUID =
        (response.entity as InformationRequestParticipantAccountUpgradeDto).participantAccountLinkId

    private fun reasonCodeOf(response: Response): String? = (response.entity as ResponseError).reasonCode

    private fun fixture(): UpgradeFixture = dataSource.connection.use { connection ->
        val runtime = SubmissionRuntimeSqlFixture(connection)
        val participantId = UUID.randomUUID()
        val appUserId = UUID.randomUUID()
        val email = "upgrade-${participantId.toString().take(8)}@process.test"
        execute(
            connection, """
            INSERT INTO external_participant (id, owner_organization_id, email, email_lower, is_active, created_date, email_verified_at)
            VALUES (?, ?, ?, ?, TRUE, now(), now())
        """.trimIndent(), participantId, runtime.template.organizationId, email, email
        )
        execute(
            connection, """
            INSERT INTO app_user
                (id, is_active, created_date, email, email_verification_completed, is_temporary,
                 sign_in_attempts, exchange_version, multifactor_authentication_type,
                 is_password_temporary, email_mfa_fallback_enabled)
            VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
        """.trimIndent(), appUserId, email
        )
        val shareId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'PARTICIPANT', ?, 'CONTRIBUTOR', 'DIRECT', 'ACTIVE', now())
        """.trimIndent(), shareId, runtime.requestId, participantId
        )
        val partyId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, share_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'CONTRIBUTOR', 'PARTICIPANT', ?, ?, TRUE, 1, now(), now(), now())
        """.trimIndent(), partyId, runtime.requestId, participantId, shareId
        )
        val linkIds = List(2) { UUID.randomUUID() }
        linkIds.forEach { linkId ->
            execute(
                connection, """
                INSERT INTO share_link
                    (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                     contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength)
                VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 1, FALSE, 0, 0, 0, 5, now() + interval '30 days', now(), 'EMAIL_OTP')
            """.trimIndent(), linkId, shareId, CommandRequestFingerprint.sha256Hex("upgrade-token-$linkId")
            )
        }
        val sessionId = UUID.randomUUID()
        val secret =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        execute(
            connection, """
            INSERT INTO request_access_session
                (id, share_link_id, participant_principal_kind, participant_principal_id, verification_strength,
                 issued_at, expires_at, credential_hash, created_at)
            VALUES (?, ?, 'PARTICIPANT', ?, 'EMAIL_OTP', now(), now() + interval '1 hour', ?, now())
        """.trimIndent(), sessionId, linkIds.first(), participantId, CommandRequestFingerprint.sha256Hex(secret)
        )
        UpgradeFixture(
            runtime.requestId,
            runtime.exchangeId,
            partyId,
            appUserId,
            linkIds,
            sessionId,
            "$sessionId.$secret"
        )
    }

    private inner class UpgradeFixture(
        val requestId: UUID,
        val exchangeId: UUID,
        val partyId: UUID,
        val appUserId: UUID,
        val linkIds: List<UUID>,
        val sessionId: UUID,
        val sessionToken: String,
    ) {
        fun command(key: String) = UpgradeInformationRequestParticipantAccountCommand(
            requestId = requestId,
            sessionId = sessionId,
            sessionCredential = sessionToken,
            access = RequestAccessContext(PrincipalRef.user(appUserId), AuthorizationContext()),
            precondition = CommandPrecondition.ExpectedRevision(RevisionETag.of(partyId, 1L)),
            idempotencyKey = key,
        )

        fun linkThroughAdapter(
            key: String,
            sessionToken: String = this.sessionToken,
            accountId: UUID = appUserId,
            namedSessionId: UUID = sessionId,
        ): Response {
            val accessContexts = mock<InformationRequestAccessContextFactory>()
            whenever(accessContexts.currentAuthenticated())
                .thenReturn(RequestAccessContext(PrincipalRef.user(accountId), AuthorizationContext()))
            return InformationRequestParticipantAccountLinkResource(upgrades, accessContexts).upgrade(
                requestId.toString(),
                UpgradeInformationRequestParticipantAccountRequest(namedSessionId),
                RevisionETag.of(partyId, 1L),
                key,
                sessionToken,
            )
        }

        fun history(): List<String> = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT mutation, reason_code FROM information_request_transition WHERE information_request_id = ? ORDER BY sequence_number",
            ).use { statement ->
                statement.setObject(1, requestId)
                statement.executeQuery().use { rows ->
                    generateSequence {
                        if (rows.next()) "${rows.getString(1)}|${
                            rows.getString(2).orEmpty()
                        }"
                        else null
                    }.toList()
                }
            }
        }

        fun assertRecords(eventTypeKey: String, count: Int) {
            dataSource.connection.use { connection ->
                assertEquals(
                    count, queryInt(
                        connection,
                        """
                    SELECT count(*) FROM audit_outbox a
                    JOIN information_request_transition t ON t.id::text = a.business_transaction_id
                    JOIN workflow_event_outbox e ON e.event_id = a.event_id
                    WHERE a.target_id = ? AND a.event_type_key = ? AND a.actor_kind = 'HUMAN' AND a.actor_id = ?
                      AND a.session_id = ? AND t.party_id = ? AND t.actor_kind = 'USER' AND t.actor_id = ?
                      AND e.ordering_key = ?
                """.trimIndent(),
                        requestId.toString(),
                        eventTypeKey,
                        appUserId,
                        sessionId.toString(),
                        partyId,
                        appUserId,
                        InformationRequestTransitionHistoryService.orderingKeyOf(requestId)
                    )
                )
            }
        }

        fun payloadValues(eventTypeKey: String, key: String): Set<String?> = dataSource.connection.use { connection ->
            queryStrings(
                connection,
                "SELECT payload_json::jsonb ->> ? FROM audit_outbox WHERE target_id = ? AND event_type_key = ?",
                key,
                requestId.toString(),
                eventTypeKey
            )
        }

        fun linkStatus(linkId: UUID): String? =
            dataSource.connection.use { queryString(it, "SELECT status FROM share_link WHERE id = ?", linkId) }

        fun sessionRevoked(): Boolean = dataSource.connection.use {
            queryString(it, "SELECT revoked_at::text FROM request_access_session WHERE id = ?", sessionId) != null
        }

        fun sessionLockAvailable(): Boolean = dataSource.connection.use { probe ->
            probe.autoCommit = false
            try {
                queryString(
                    probe,
                    "SELECT CAST(id AS text) FROM request_access_session WHERE id = ? FOR UPDATE NOWAIT",
                    sessionId
                )
                true
            } catch (_: SQLException) {
                false
            } finally {
                probe.rollback()
            }
        }

        fun accountLinkCount(): Int = dataSource.connection.use {
            queryInt(it, "SELECT count(*) FROM participant_account_link WHERE app_user_id = ?", appUserId)
        }

        fun userShareCount(): Int = dataSource.connection.use {
            queryInt(
                it,
                "SELECT count(*) FROM share WHERE resource_id = ? AND principal_kind = 'USER' AND principal_id = ?",
                requestId,
                appUserId
            )
        }
    }

    private fun <T> inParallel(count: Int, call: () -> T): List<Result<T>> {
        val pool = Executors.newFixedThreadPool(count)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val calls = (1..count).map {
                pool.submit<Result<T>> {
                    start.await()
                    val context = io.quarkus.arc.Arc.container().requestContext()
                    context.activate()
                    try {
                        runCatching(call)
                    } finally {
                        context.terminate()
                    }
                }
            }
            start.countDown()
            return calls.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
