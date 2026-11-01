package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.informationrequest.noauth.IssuedRequestAccessSession
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import io.quarkus.narayana.jta.QuarkusTransaction
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import javax.sql.DataSource

internal class NoAuthSessionSqlScenario(
    private val dataSource: DataSource,
    links: ShareLinkService,
    private val sessions: RequestAccessSessionService,
    beforePublish: (Connection, SubmissionRuntimeSqlFixture) -> Unit = { _, _ -> },
    prepare: (Connection, SubmissionRuntimeSqlFixture) -> Unit = { _, _ -> },
) {
    val rawToken = "session-boundary-${UUID.randomUUID()}"
    val runtime: SubmissionRuntimeSqlFixture
    val participant: PrincipalRef
    val link: ShareLink

    init {
        val prepared = dataSource.connection.use { connection ->
            val fixture = SubmissionRuntimeSqlFixture(connection, beforePublish = { beforePublish(connection, it) })
            prepare(connection, fixture)
            val participantId = contributorAsParticipant(connection, fixture)
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, issued_at, created_at)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.requestId,
                fixture.template.organizationId,
            )
            val shareId = requireNotNull(
                queryString(
                    connection,
                    "SELECT share_id::text FROM information_request_party WHERE id = ?",
                    fixture.contributorPartyId,
                ),
            )
            Triple(fixture, participantId, UUID.fromString(shareId))
        }
        runtime = prepared.first
        participant = PrincipalRef.participant(prepared.second)
        link = QuarkusTransaction.requiringNew().call {
            links.create(
                ShareLink().apply {
                    shareId = prepared.third
                    tokenHash = CommandRequestFingerprint.sha256Hex(rawToken)
                    linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
                    verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP
                    expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
                },
            )
        }
    }

    val requestId: UUID get() = runtime.requestId

    fun issueSession(): IssuedRequestAccessSession =
        sessions.issue(link, participant, RequestAccessSessionVerificationStrength.EMAIL_OTP, null)

    fun expireLink() {
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE share_link SET expires_at = now() - interval '1 second' WHERE id = ?", link.id)
        }
    }

    fun responsesWithNarrative(narrative: String): Int = dataSource.connection.use { connection ->
        queryInt(
            connection,
            "SELECT count(*) FROM information_request_response WHERE information_request_id = ? AND narrative = ?",
            requestId,
            narrative,
        )
    }

    fun currentDocumentNarrative(): String? = dataSource.connection.use { connection ->
        queryString(
            connection,
            """
            SELECT narrative FROM information_request_response
            WHERE information_request_requirement_id = ?
            ORDER BY response_revision DESC
            LIMIT 1
            """.trimIndent(),
            runtime.documentRequirementId,
        )
    }
}
