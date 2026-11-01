package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.noauth.IssueInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.ReplaceInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.RevokeInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.RotateInformationRequestBootstrapShareLinkCommand
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestAccessLinkStandingTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var links: InformationRequestBootstrapShareLinkService

    @Inject
    lateinit var parties: InformationRequestPartyRepository

    @Test
    fun `an active request issues, resends, and replaces an access link`() {
        val fixture = fixture()

        val issued = issue(fixture, "issue-while-active")
        val rotated = rotate(fixture, issued.shareLink.id, "rotate-while-active")
        val replaced = replace(fixture, rotated.shareLink.id, "replace-while-active")

        assertEquals(ShareLinkStatus.ACTIVE, replaced.shareLink.status)
    }

    @Test
    fun `a request whose grant was revoked issues, resends, and replaces no access link but still revokes one`() {
        val unlinked = fixture()
        val linked = fixture()
        val issued = issue(linked, "issue-before-revocation")
        revokeGrant(unlinked)
        revokeGrant(linked)

        listOf(
            { issue(unlinked, "issue-after-revocation") },
            { rotate(linked, issued.shareLink.id, "rotate-after-revocation") },
            { replace(linked, issued.shareLink.id, "replace-after-revocation") },
        ).forEach { attempt ->
            val refusal = assertThrows(InformationRequestLifecycleException::class.java) { attempt() }
            assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, refusal.reasonCode)
        }
        assertEquals(
            ShareLinkStatus.REVOKED,
            revoke(linked, issued.shareLink.id, "revoke-after-revocation").shareLink.status
        )
    }

    @Test
    fun `an operationally suspended owner issues, resends, and replaces no access link but still revokes one`() {
        val unlinked = fixture()
        val linked = fixture()
        val issued = issue(linked, "issue-before-suspension")
        suspendOwner(unlinked)
        suspendOwner(linked)

        listOf(
            { issue(unlinked, "issue-while-suspended") },
            { rotate(linked, issued.shareLink.id, "rotate-while-suspended") },
            { replace(linked, issued.shareLink.id, "replace-while-suspended") },
        ).forEach { attempt -> assertThrows(SubscriptionDenialException::class.java) { attempt() } }
        assertEquals(
            ShareLinkStatus.REVOKED,
            revoke(linked, issued.shareLink.id, "revoke-while-suspended").shareLink.status
        )
    }

    private fun fixture(): SubmissionRuntimeSqlFixture = dataSource.connection.use { connection ->
        SubmissionRuntimeSqlFixture(connection).also { fixture ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.exchangeId,
                fixture.template.userId,
            )
            contributorAsParticipant(connection, fixture)
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, issued_at, created_at, continuation_actions)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, now(), now(),
                        ARRAY['RESPOND', 'SUBMIT', 'ATTEST', 'REVIEW', 'ASSIGN_PARTY_WITHIN_CAP',
                              'UPLOAD_WITHIN_ALLOWANCE', 'NON_EXPANDING_AMENDMENT', 'SEND_REMINDER',
                              'CANCEL', 'SUPERSEDE']::varchar(64)[])
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.requestId,
                fixture.template.organizationId,
            )
        }
    }

    private fun revokeGrant(fixture: SubmissionRuntimeSqlFixture) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE request_execution_grant SET revoked_at = now(), revoked_reason = 'process.stopped' WHERE request_id = ?",
                fixture.requestId,
            )
        }
    }

    private fun suspendOwner(fixture: SubmissionRuntimeSqlFixture) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "DELETE FROM organization_subscription_policy WHERE organization_id = ?",
                fixture.template.organizationId
            )
            execute(
                connection,
                """
                INSERT INTO organization_subscription_policy
                    (id, organization_id, tier_code, subscription_status, max_users, current_period_end, created_date, updated_date)
                VALUES (?, ?, 'BUSINESS', 'SUSPENDED', 5, now() + interval '30 days', now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.template.organizationId,
            )
        }
    }

    private fun manager(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(
            PrincipalRef.user(fixture.template.userId),
            AuthorizationContext(sessionRef = "link-standing-session")
        )

    private fun partyETag(fixture: SubmissionRuntimeSqlFixture) = QuarkusTransaction.requiringNew().call {
        CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(requireNotNull(parties.findById(fixture.contributorPartyId))))
    }

    private fun issue(fixture: SubmissionRuntimeSqlFixture, key: String) = links.issue(
        IssueInformationRequestBootstrapShareLinkCommand(
            fixture.requestId,
            fixture.contributorPartyId,
            manager(fixture),
            partyETag(fixture),
            key
        ),
    )

    private fun rotate(fixture: SubmissionRuntimeSqlFixture, linkId: UUID, key: String) = links.rotate(
        RotateInformationRequestBootstrapShareLinkCommand(
            fixture.requestId,
            linkId,
            manager(fixture),
            partyETag(fixture),
            key
        ),
    )

    private fun replace(fixture: SubmissionRuntimeSqlFixture, linkId: UUID, key: String) = links.replace(
        ReplaceInformationRequestBootstrapShareLinkCommand(
            fixture.requestId,
            linkId,
            manager(fixture),
            partyETag(fixture),
            key
        ),
    )

    private fun revoke(fixture: SubmissionRuntimeSqlFixture, linkId: UUID, key: String) = links.revoke(
        RevokeInformationRequestBootstrapShareLinkCommand(
            fixture.requestId,
            linkId,
            manager(fixture),
            partyETag(fixture),
            key
        ),
    )
}
