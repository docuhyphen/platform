package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRefreshRuleCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.DueRefreshRule
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRefreshSchedulerFairnessTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var refreshes: InformationRequestRefreshScheduler

    @Test
    fun `a later due refresh is created although fifty earlier rules stay blocked, and the blocked rules back off`() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val blocked = (1..BLOCKED_RULES).map { dueRule() }
        val eligible = dueRule()
        blocked.forEach(::suspendOwner)

        refreshes.processDueRefreshes(now)
        val afterFirstPass = refreshesOf(eligible)
        refreshes.processDueRefreshes(now)

        assertEquals(0, afterFirstPass)
        assertEquals(1, refreshesOf(eligible))
        assertEquals(setOf(1 to now.plus(Duration.ofHours(1))), blocked.map(::retryOf).toSet())

        refreshes.processDueRefreshes(now.plus(Duration.ofHours(1)))
        refreshes.processDueRefreshes(now.plus(Duration.ofHours(2)))

        assertEquals(1, refreshesOf(eligible))
        assertEquals(0, blocked.sumOf(::refreshesOf))
        assertEquals(setOf(2 to now.plus(Duration.ofHours(3))), blocked.map(::retryOf).toSet())
        assertEquals(0 to null, retryOf(eligible))
    }

    @Test
    fun `a blocked rule waits for its retry time, then refreshes once its block has cleared and starts afresh`() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val rule = dueRule()
        suspendOwner(rule)

        refreshes.processDueRefreshes(now)
        restoreOwner(rule)
        refreshes.processDueRefreshes(now.plus(Duration.ofMinutes(30)))

        assertEquals(0, refreshesOf(rule))
        assertEquals(1 to now.plus(Duration.ofHours(1)), retryOf(rule))

        refreshes.processDueRefreshes(now.plus(Duration.ofHours(1)))

        assertEquals(1, refreshesOf(rule))
        assertEquals(0 to null, retryOf(rule))
    }

    @Test
    fun `a rule whose Exchange can no longer take requests is not attempted and does not back off`() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val ended = dueRule()
        val deleted = dueRule()
        endExchange(ended)
        deleteExchange(deleted)

        refreshes.processDueRefreshes(now)

        assertEquals(0, refreshesOf(ended) + refreshesOf(deleted))
        assertEquals(0 to null, retryOf(ended))
        assertEquals(0 to null, retryOf(deleted))
    }

    @Test
    fun `an expired request whose evidence nears its expiry is refreshed`() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val rule = dueRule()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'EXPIRED', expired_at = now() WHERE id = ?",
                rule.requestId
            )
        }

        refreshes.processDueRefreshes(now)

        assertEquals(1, refreshesOf(rule))
    }

    @Test
    fun `a rule whose lead time reaches past the calendar does not stop the pass`() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val farReaching = dueRule(leadDays = Int.MAX_VALUE)
        val ordinary = dueRule()

        assertDoesNotThrow { refreshes.processDueRefreshes(now) }

        assertEquals(1, refreshesOf(farReaching))
        assertEquals(1, refreshesOf(ordinary))
    }

    private fun dueRule(leadDays: Int = 30): DueRefreshRule {
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, evidenceExpiresOn = LocalDate.now(ZoneOffset.UTC).plusDays(10))
        }
        val services = runtime.build(fixture.requestId)
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access(fixture.attestorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(fixture.requestId)),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = access(fixture.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "submit-${UUID.randomUUID()}",
                ),
            )
        }
        val rule = QuarkusTransaction.requiringNew().call {
            services.followUps.defineRefreshRule(
                DefineInformationRequestRefreshRuleCommand(
                    requestId = fixture.requestId,
                    requirementKey = "supporting-record",
                    leadDays = leadDays,
                    access = access(fixture.template.userId),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(fixture.requestId)),
                    idempotencyKey = "rule-${UUID.randomUUID()}",
                ),
            )
        }.refreshRule
        return DueRefreshRule(fixture.requestId, fixture.exchangeId, fixture.template.organizationId, rule.id)
    }

    private fun suspendOwner(rule: DueRefreshRule) = setOwnerStatus(rule, "SUSPENDED")

    private fun restoreOwner(rule: DueRefreshRule) = setOwnerStatus(rule, "ACTIVE")

    private fun setOwnerStatus(rule: DueRefreshRule, status: String) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "DELETE FROM organization_subscription_policy WHERE organization_id = ?",
                rule.organizationId
            )
            execute(
                connection,
                """
                INSERT INTO organization_subscription_policy
                    (id, organization_id, tier_code, subscription_status, max_users, current_period_end, created_date, updated_date)
                VALUES (?, ?, 'BUSINESS', ?, 5, now() + interval '30 days', now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                rule.organizationId,
                status,
            )
        }
    }

    private fun deleteExchange(rule: DueRefreshRule) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE exchange SET is_deleted = TRUE, date_deleted = now() WHERE id = ?",
                rule.exchangeId
            )
        }
    }

    private fun endExchange(rule: DueRefreshRule) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE exchange SET status = 'ENDED' WHERE id = ?",
                rule.exchangeId
            )
        }
    }

    private fun refreshesOf(rule: DueRefreshRule): Int = dataSource.connection.use { connection ->
        queryInt(
            connection,
            "SELECT count(*) FROM information_request_lineage WHERE source_request_id = ? AND lineage_kind = 'REFRESH'",
            rule.requestId,
        )
    }

    private fun retryOf(rule: DueRefreshRule): Pair<Int, Instant?> = dataSource.connection.use { connection ->
        val attempts =
            queryInt(connection, "SELECT attempt_count FROM information_request_refresh_rule WHERE id = ?", rule.ruleId)
        val next = queryString(
            connection,
            "SELECT to_char(next_attempt_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM information_request_refresh_rule WHERE id = ?",
            rule.ruleId,
        )
        attempts to next?.let(Instant::parse)
    }

    private fun submissionETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

    private fun access(userId: UUID) =
        RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(sessionRef = "refresh-session"))

    private companion object {
        const val BLOCKED_RULES = 50
    }
}
