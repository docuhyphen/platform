package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestContactProofRecordingTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var contactProof: InformationRequestContactProofService

    @Inject
    lateinit var otp: OtpService

    @Inject
    lateinit var audit: AuditRecorder

    @Test
    fun `issuing a contact challenge records the link's party through the public link without contact material`() {
        val fixture = fixture()
        val email = installEmailCapture()

        contactProof.issueChallenge(fixture.rawToken)

        val body = argumentCaptor<String>()
        verify(email).sendEmail(any(), any(), body.capture(), any())
        val code = requireNotNull(Regex("\\d{6}").find(body.firstValue)).value
        assertEquals(listOf("ISSUE_CONTACT_CHALLENGE|"), fixture.history())
        fixture.assertRecord("information_request.contact_challenge.issue", 1, null)
        assertEquals(
            setOf(fixture.linkId.toString()),
            fixture.payloadValues("information_request.contact_challenge.issue", "shareLinkId")
        )
        assertEquals(setOf("1"), fixture.payloadValues("information_request.contact_challenge.issue", "challengeCount"))
        fixture.assertNoContactMaterial(listOf(code, fixture.email, fixture.storedCodeHash().orEmpty()))
    }

    @Test
    fun `every code sent after an earlier verification records its own challenge`() {
        val fixture = fixture()
        val email = installEmailCapture()

        contactProof.issueChallenge(fixture.rawToken)
        val body = argumentCaptor<String>()
        verify(email).sendEmail(any(), any(), body.capture(), any())
        contactProof.verifyChallenge(fixture.rawToken, requireNotNull(Regex("\\d{6}").find(body.firstValue)).value)
        contactProof.issueChallenge(fixture.rawToken)

        assertEquals(
            listOf("ISSUE_CONTACT_CHALLENGE|", "ISSUE_ACCESS_SESSION|", "ISSUE_CONTACT_CHALLENGE|"),
            fixture.history(),
        )
        fixture.assertRecord("information_request.contact_challenge.issue", 2, null)
    }

    @Test
    fun `a contact code is sent only once its challenge is committed and the request is no longer locked`() {
        val fixture = fixture()
        val email = installEmailCapture()
        val seenAtSend = mutableListOf<Pair<Int, Boolean>>()
        doAnswer {
            seenAtSend += CompletableFuture
                .supplyAsync { fixture.challengeCount() to fixture.exchangeLockable() }
                .get(10, TimeUnit.SECONDS)
            null
        }.whenever(email).sendEmail(any(), any(), any(), any())

        contactProof.issueChallenge(fixture.rawToken)

        assertEquals(listOf(1 to true), seenAtSend)
    }

    @Test
    fun `each refused code is recorded with its attempt and the lockout outlives the refused transactions`() {
        val fixture = fixture()
        fixture.storeCode("654321")

        repeat(5) {
            val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
                contactProof.verifyChallenge(fixture.rawToken, "000000")
            }
            assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_INVALID, refusal.reasonCode)
        }

        assertEquals(List(5) { "REFUSE_CONTACT_CHALLENGE|CONTACT_PROOF_INVALID" }, fixture.history())
        fixture.assertRecord("information_request.contact_challenge.refuse", 5, null)
        assertEquals(
            (1..5).map(Int::toString).toSet(),
            fixture.payloadValues("information_request.contact_challenge.refuse", "failedAttempt")
        )
        assertEquals(
            1,
            fixture.payloadValues("information_request.contact_challenge.refuse", "lockedUntil").filterNotNull().size
        )
        assertNotNull(fixture.lockedUntil())
    }

    @Test
    fun `a verified code records the issued session under its own session reference`() {
        val fixture = fixture()
        fixture.storeCode("654321")

        val issued = contactProof.verifyChallenge(fixture.rawToken, "654321")

        assertEquals(listOf("ISSUE_ACCESS_SESSION|"), fixture.history())
        fixture.assertRecord("information_request.access_session.issue", 1, issued.session.id.toString())
        assertEquals(
            setOf(issued.session.id.toString()),
            fixture.payloadValues("information_request.access_session.issue", "accessSessionId")
        )
        assertEquals(
            setOf(fixture.linkId.toString()),
            fixture.payloadValues("information_request.access_session.issue", "shareLinkId")
        )
        assertEquals(setOf("1"), fixture.payloadValues("information_request.access_session.issue", "usedCount"))
        fixture.assertNoContactMaterial(listOf(issued.sessionToken.substringAfter('.'), fixture.email))
    }

    @Test
    fun `a session that cannot be recorded is refused and leaves the code and use budget unspent`() {
        val fixture = fixture()
        fixture.storeCode("654321")
        installDegradedAudit("information_request.access_session.issue")

        val refusal = assertThrows(Throwable::class.java) { contactProof.verifyChallenge(fixture.rawToken, "654321") }

        assertTrue(generateSequence(refusal) { it.cause }.any { it is AuditCaptureFailedException })
        assertTrue(fixture.history().isEmpty())
        assertEquals(0, fixture.usedCount())
        assertNotNull(fixture.storedCodeHash())
        assertEquals(0, fixture.sessionCount())
    }

    @Test
    fun `a challenge that cannot be recorded is refused before any code is sent`() {
        val fixture = fixture()
        val email = installEmailCapture()
        installDegradedAudit("information_request.contact_challenge.issue")

        val refusal = assertThrows(Throwable::class.java) { contactProof.issueChallenge(fixture.rawToken) }

        assertTrue(generateSequence(refusal) { it.cause }.any { it is AuditCaptureFailedException })
        verify(email, never()).sendEmail(any(), any(), any(), any())
        assertTrue(fixture.history().isEmpty())
        assertNull(fixture.storedCodeHash())
        assertEquals(0, fixture.challengeCount())
    }

    private fun installEmailCapture(): EmailService = mock<EmailService>().also {
        QuarkusMock.installMockForType(it, EmailService::class.java)
    }

    private fun installDegradedAudit(eventTypeKey: String) {
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenAnswer { invocation ->
            val draft = invocation.getArgument<AuditEventDraft>(0)
            if (draft.eventTypeKey == eventTypeKey) AuditCaptureResult.Degraded(
                draft.eventId,
                "Audit outbox unavailable"
            )
            else AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
        }
        QuarkusMock.installMockForType(recorder, AuditRecorder::class.java)
    }

    private fun fixture(): ContactFixture = dataSource.connection.use { connection ->
        val runtime = SubmissionRuntimeSqlFixture(connection)
        val participantId = UUID.randomUUID()
        val email = "respondent-${participantId.toString().take(8)}@process.test"
        execute(
            connection, """
            INSERT INTO external_participant (id, owner_organization_id, email, email_lower, is_active, created_date)
            VALUES (?, ?, ?, ?, TRUE, now())
        """.trimIndent(), participantId, runtime.template.organizationId, email, email
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
        val rawToken = "contact-token-${UUID.randomUUID()}"
        val linkId = UUID.randomUUID()
        execute(
            connection, """
            INSERT INTO share_link
                (id, share_id, token_hash, status, link_mode, used_count, require_mfa, contact_otp_failed_attempts,
                 contact_otp_challenge_count, rotation_count, max_uses, expires_at, created_at, verification_strength)
            VALUES (?, ?, ?, 'ACTIVE', 'VERIFICATION_BOOTSTRAP', 0, FALSE, 0, 0, 0, 5, now() + interval '30 days', now(), 'EMAIL_OTP')
        """.trimIndent(), linkId, shareId, CommandRequestFingerprint.sha256Hex(rawToken)
        )
        ContactFixture(runtime.requestId, partyId, participantId, email, rawToken, linkId)
    }

    private inner class ContactFixture(
        val requestId: UUID,
        val partyId: UUID,
        val participantId: UUID,
        val email: String,
        val rawToken: String,
        val linkId: UUID,
    ) {
        fun storeCode(code: String) {
            dataSource.connection.use { connection ->
                execute(
                    connection,
                    "UPDATE share_link SET contact_otp_hash = ?, contact_otp_expires_at = now() + interval '10 minutes', contact_otp_challenge_count = 1 WHERE id = ?",
                    otp.hashOtp(code),
                    linkId
                )
            }
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

        fun assertRecord(eventTypeKey: String, count: Int, sessionReference: String?) {
            dataSource.connection.use { connection ->
                assertEquals(
                    count, queryInt(
                        connection,
                        """
                    SELECT count(*) FROM audit_outbox a
                    JOIN information_request_transition t ON t.id::text = a.business_transaction_id
                    JOIN workflow_event_outbox e ON e.event_id = a.event_id
                    WHERE a.target_id = ? AND a.event_type_key = ? AND a.actor_kind = 'PUBLIC_LINK' AND a.actor_id = ?
                      AND COALESCE(a.session_id, '') = ? AND t.party_id = ? AND t.actor_kind = 'PARTICIPANT'
                      AND t.actor_id = ? AND e.ordering_key = ?
                """.trimIndent(),
                        requestId.toString(),
                        eventTypeKey,
                        participantId,
                        sessionReference.orEmpty(),
                        partyId,
                        participantId,
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

        fun assertNoContactMaterial(secrets: List<String>) {
            dataSource.connection.use { connection ->
                val payloads = queryStrings(
                    connection,
                    "SELECT payload_json FROM audit_outbox WHERE target_id = ?",
                    requestId.toString()
                ) +
                        queryStrings(
                            connection,
                            "SELECT envelope_json FROM workflow_event_outbox WHERE ordering_key = ?",
                            InformationRequestTransitionHistoryService.orderingKeyOf(requestId)
                        )
                secrets.filter { it.isNotBlank() }.forEach { secret ->
                    assertTrue(
                        payloads.none { it.contains(secret) },
                        "Recorded payloads must not contain $secret"
                    )
                }
            }
        }

        fun storedCodeHash(): String? = dataSource.connection.use {
            queryString(
                it,
                "SELECT contact_otp_hash FROM share_link WHERE id = ?",
                linkId
            )
        }

        fun lockedUntil(): String? = dataSource.connection.use {
            queryString(
                it,
                "SELECT contact_otp_locked_until::text FROM share_link WHERE id = ?",
                linkId
            )
        }

        fun usedCount(): Int =
            dataSource.connection.use { queryInt(it, "SELECT used_count FROM share_link WHERE id = ?", linkId) }

        fun challengeCount(): Int = dataSource.connection.use {
            queryInt(
                it,
                "SELECT contact_otp_challenge_count FROM share_link WHERE id = ?",
                linkId
            )
        }

        fun sessionCount(): Int = dataSource.connection.use {
            queryInt(
                it,
                "SELECT count(*) FROM request_access_session WHERE share_link_id = ?",
                linkId
            )
        }

        fun exchangeLockable(): Boolean = dataSource.connection.use { probe ->
            probe.autoCommit = false
            try {
                probe.prepareStatement(
                    """
                    SELECT exchange.id FROM exchange JOIN information_request request ON request.exchange_id = exchange.id
                    WHERE request.id = ? FOR UPDATE OF exchange NOWAIT
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, requestId)
                    statement.executeQuery().close()
                }
                true
            } catch (_: java.sql.SQLException) {
                false
            } finally {
                probe.rollback()
                probe.autoCommit = true
            }
        }
    }
}
