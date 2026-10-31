package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRecurrenceCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.DefineInformationRequestRefreshRuleCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.FollowUpDefinitionScenario
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.FollowUpDefinitionWrites
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestFollowUpDefinitionTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Test
    fun `a recurrence definition is not replayed once its caller can no longer view the request`() {
        val scenario = scenario()
        val command = recurrence(scenario, scenario.manager, "recurrence-before-removal")
        QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(command) }
        val recorded = writesOf(scenario)

        withdrawAccess(scenario.manager)
        assertFalse(permits(scenario, scenario.manager, Action.INFORMATION_REQUEST_VIEW))

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(command) }
        }
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `a refresh rule definition is not replayed once its caller can no longer view the request`() {
        val scenario = scenario()
        val command = refreshRule(scenario, scenario.manager, "refresh-rule-before-removal")
        QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRefreshRule(command) }
        val recorded = writesOf(scenario)

        withdrawAccess(scenario.manager)
        assertFalse(permits(scenario, scenario.manager, Action.INFORMATION_REQUEST_VIEW))

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRefreshRule(command) }
        }
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `a caller who keeps only read access replays the original definitions without writing again`() {
        val scenario = scenario()
        val recurrenceCommand = recurrence(scenario, scenario.manager, "recurrence-kept-readable")
        val ruleCommand = refreshRule(scenario, scenario.manager, "refresh-rule-kept-readable")
        val recurrence =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(recurrenceCommand) }
        val rule = QuarkusTransaction.requiringNew().call {
            scenario.services.followUps.defineRefreshRule(ruleCommand.copy(precondition = currentPrecondition(scenario)))
        }
        val recorded = writesOf(scenario)

        retainReadOnly(scenario, scenario.manager)
        assertTrue(permits(scenario, scenario.manager, Action.INFORMATION_REQUEST_VIEW))
        assertFalse(permits(scenario, scenario.manager, Action.INFORMATION_REQUEST_SUPERSEDE))

        val replayedRecurrence =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(recurrenceCommand) }
        val replayedRule = QuarkusTransaction.requiringNew().call {
            scenario.services.followUps.defineRefreshRule(ruleCommand.copy(precondition = currentPrecondition(scenario)))
        }

        assertEquals(recurrence.recurrence.id, replayedRecurrence.recurrence.id)
        assertEquals(rule.refreshRule.id, replayedRule.refreshRule.id)
        assertEquals(recorded, writesOf(scenario))
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.followUps.defineRefreshRule(
                    ruleCommand.copy(
                        precondition = currentPrecondition(scenario),
                        idempotencyKey = "refresh-rule-after-read-only"
                    ),
                )
            }
        }
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `a retried definition answers with the request ETag as it stands after a later change`() {
        val scenario = scenario()
        val recurrenceCommand = recurrence(scenario, scenario.manager, "recurrence-then-change")
        val recurrence =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(recurrenceCommand) }
        val ruleCommand = refreshRule(scenario, scenario.manager, "refresh-rule-then-change")
            .copy(precondition = currentPrecondition(scenario))
        val rule = QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRefreshRule(ruleCommand) }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET aggregate_revision = aggregate_revision + 1 WHERE id = ?",
                scenario.requestId
            )
        }

        val replayedRecurrence =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(recurrenceCommand) }
        val replayedRule =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRefreshRule(ruleCommand) }

        assertEquals(recurrence.recurrence.id, replayedRecurrence.recurrence.id)
        assertEquals(rule.refreshRule.id, replayedRule.refreshRule.id)
        assertNotEquals(rule.requestETag, replayedRule.requestETag)
        assertEquals(currentETag(scenario), replayedRecurrence.requestETag)
        assertEquals(currentETag(scenario), replayedRule.requestETag)
    }

    @Test
    fun `an idempotency key replays a definition only for the caller who used it`() {
        val scenario = scenario()
        val command = recurrence(scenario, scenario.manager, "shared-recurrence-key")
        QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(command) }
        val recorded = writesOf(scenario)

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.followUps.defineRecurrence(
                    command.copy(
                        access = access(scenario.contributor),
                        precondition = currentPrecondition(scenario)
                    )
                )
            }
        }
        val secondManager = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.followUps.defineRecurrence(
                    command.copy(
                        access = access(scenario.secondManager),
                        precondition = currentPrecondition(scenario)
                    )
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID, secondManager.reasonCode)
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `each definition advances the request validator it was checked against and answers the resulting one`() {
        val scenario = scenario()
        val before = currentETag(scenario)
        val recurrenceCommand = recurrence(scenario, scenario.manager, "validated-recurrence")
        val recurrence =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(recurrenceCommand) }
        val afterRecurrence = currentETag(scenario)

        assertNotEquals(before, afterRecurrence)
        assertEquals(afterRecurrence, recurrence.requestETag)
        val stale = assertThrows(CommandPreconditionException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.followUps.defineRefreshRule(
                    refreshRule(
                        scenario,
                        scenario.manager,
                        "stale-refresh-rule"
                    ).copy(precondition = CommandPrecondition.ExpectedRevision(before)),
                )
            }
        }
        assertEquals(CommandPreconditionException.Kind.STALE, stale.kind)
        assertEquals(afterRecurrence, stale.currentETag)

        val rule = QuarkusTransaction.requiringNew().call {
            scenario.services.followUps.defineRefreshRule(
                refreshRule(
                    scenario,
                    scenario.manager,
                    "validated-refresh-rule"
                )
            )
        }
        val afterRule = currentETag(scenario)
        val replayed =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(recurrenceCommand) }

        assertNotEquals(afterRecurrence, afterRule)
        assertEquals(afterRule, rule.requestETag)
        assertEquals(recurrence.recurrence.id, replayed.recurrence.id)
        assertEquals(afterRule, replayed.requestETag)
        assertEquals(afterRule, currentETag(scenario))
        assertEquals(2, writesOf(scenario).scheduleTransitions)
    }

    @Test
    fun `two definitions checked against the same validator are never both recorded`() {
        val scenario = scenario()
        val initial = writesOf(scenario)
        val read = CommandPrecondition.ExpectedRevision(currentETag(scenario))
        val recurrenceCommand = recurrence(scenario, scenario.manager, "racing-recurrence").copy(precondition = read)
        val ruleCommand = refreshRule(scenario, scenario.manager, "racing-refresh-rule").copy(precondition = read)
        val firstHoldsLock = CountDownLatch(1)

        val first = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew().run {
                        scenario.services.followUps.defineRecurrence(recurrenceCommand)
                        firstHoldsLock.countDown()
                        Thread.sleep(HOLD_MILLIS)
                    }
                }
            }.exceptionOrNull()
        }
        assertTrue(firstHoldsLock.await(30, TimeUnit.SECONDS))
        val second = CompletableFuture.supplyAsync {
            runCatching {
                withRequestContext {
                    QuarkusTransaction.requiringNew()
                        .call { scenario.services.followUps.defineRefreshRule(ruleCommand) }
                }
            }.exceptionOrNull()
        }

        assertNull(first.get(60, TimeUnit.SECONDS))
        val refusal = second.get(60, TimeUnit.SECONDS)
        assertTrue(
            refusal is CommandPreconditionException && refusal.kind == CommandPreconditionException.Kind.STALE,
            "$refusal"
        )
        val written = writesOf(scenario)
        assertEquals(1, written.recurrences)
        assertEquals(0, written.refreshRules)
        assertEquals(1, written.scheduleTransitions)
        assertEquals(initial.aggregateRevision + 1, written.aggregateRevision)
    }

    @Test
    fun `out-of-range schedules and a second rule for one requested document are refused before anything is written`() {
        val scenario = scenario()
        QuarkusTransaction.requiringNew().call {
            scenario.services.followUps.defineRefreshRule(
                refreshRule(
                    scenario,
                    scenario.manager,
                    "first-document-rule"
                )
            )
        }
        val recorded = writesOf(scenario)
        val outOfRange = listOf(
            recurrence(scenario, scenario.manager, "zero-interval").copy(intervalCount = 0),
            recurrence(scenario, scenario.manager, "negative-interval").copy(intervalCount = -2),
            recurrence(scenario, scenario.manager, "oversized-interval").copy(intervalCount = 1001),
            recurrence(scenario, scenario.manager, "no-occurrences").copy(maximumOccurrences = 0),
            recurrence(scenario, scenario.manager, "late-first-due").copy(
                firstDueAt = LocalDate.of(10000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
            ),
        )

        outOfRange.forEach { command ->
            val refusal = assertThrows(
                InformationRequestCommandRequestException::class.java,
                { QuarkusTransaction.requiringNew().call { scenario.services.followUps.defineRecurrence(command) } },
                command.idempotencyKey,
            )
            assertEquals(InformationRequestErrorCatalog.FOLLOW_UP_SCHEDULE_INVALID, refusal.reasonCode)
        }
        val negativeLead = assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.followUps.defineRefreshRule(
                    refreshRule(
                        scenario,
                        scenario.manager,
                        "negative-lead"
                    ).copy(leadDays = -1)
                )
            }
        }
        val duplicate = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.followUps.defineRefreshRule(
                    refreshRule(
                        scenario,
                        scenario.manager,
                        "second-document-rule"
                    )
                )
            }
        }

        assertEquals(InformationRequestErrorCatalog.FOLLOW_UP_SCHEDULE_INVALID, negativeLead.reasonCode)
        assertEquals(InformationRequestErrorCatalog.SUCCESSOR_SOURCE_INVALID, duplicate.reasonCode)
        assertEquals(recorded, writesOf(scenario))
    }

    private fun scenario(): FollowUpDefinitionScenario {
        val secondManager = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(
                connection,
                evidenceExpiresOn = LocalDate.now(ZoneOffset.UTC).plusDays(10)
            ).also { fixture ->
                val managerPartyId = UUID.randomUUID()
                val secondManagerPartyId = UUID.randomUUID()
                fixture.insertPartyForUser(managerPartyId, "DECISION_MAKER", fixture.template.userId)
                fixture.insertActingParty(secondManagerPartyId, "DECISION_MAKER", secondManager)
                grantRequestShare(
                    connection,
                    fixture,
                    fixture.contributorPartyId,
                    fixture.contributorUserId,
                    "CONTRIBUTOR"
                )
                grantRequestShare(connection, fixture, fixture.attestorPartyId, fixture.attestorUserId, "ATTESTOR")
                grantRequestShare(connection, fixture, managerPartyId, fixture.template.userId, "DECISION_MAKER")
                grantRequestShare(connection, fixture, secondManagerPartyId, secondManager, "DECISION_MAKER")
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
            }
        }
        return FollowUpDefinitionScenario(
            requestId = fixture.requestId,
            manager = PrincipalRef.user(fixture.template.userId),
            secondManager = PrincipalRef.user(secondManager),
            contributor = PrincipalRef.user(fixture.contributorUserId),
            services = runtime.build(fixture.requestId, centralAuthorization = authorizationService),
        )
    }

    private fun grantRequestShare(
        connection: Connection,
        fixture: SubmissionRuntimeSqlFixture,
        partyId: UUID,
        userId: UUID,
        role: String
    ) {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            fixture.requestId,
            userId,
            role,
        )
        execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
    }

    private fun withdrawAccess(principal: PrincipalRef) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE share SET status = 'REVOKED', revoked_at = now() WHERE principal_kind = 'USER' AND principal_id = ? AND status = 'ACTIVE'",
                principal.id,
            )
        }
    }

    private fun retainReadOnly(scenario: FollowUpDefinitionScenario, principal: PrincipalRef) {
        withdrawAccess(principal)
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'RECORD_OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                scenario.requestId,
                principal.id,
            )
        }
    }

    private fun permits(scenario: FollowUpDefinitionScenario, principal: PrincipalRef, action: Action): Boolean =
        QuarkusTransaction.requiringNew().call {
            authorizationService.authorize(
                principal,
                action,
                ResourceRef.informationRequest(scenario.requestId),
                AuthorizationContext(sessionRef = "follow-up-session"),
            ) is Decision.Allow
        }

    private fun writesOf(scenario: FollowUpDefinitionScenario): FollowUpDefinitionWrites =
        dataSource.connection.use { connection ->
            FollowUpDefinitionWrites(
                recurrences = queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_recurrence WHERE origin_request_id = ?",
                    scenario.requestId
                ),
                refreshRules = queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_refresh_rule WHERE information_request_id = ?",
                    scenario.requestId
                ),
                scheduleTransitions = queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_transition WHERE information_request_id = ? AND mutation = 'SCHEDULE_FOLLOW_UP'",
                    scenario.requestId,
                ),
                receipts = queryInt(
                    connection,
                    "SELECT count(*) FROM command_receipt WHERE resource_id = ?",
                    scenario.requestId
                ),
                aggregateRevision = queryInt(
                    connection,
                    "SELECT aggregate_revision FROM information_request WHERE id = ?",
                    scenario.requestId
                ),
            )
        }

    private fun currentPrecondition(scenario: FollowUpDefinitionScenario): CommandPrecondition =
        CommandPrecondition.ExpectedRevision(currentETag(scenario))

    private fun currentETag(scenario: FollowUpDefinitionScenario): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(scenario.requestId))) }

    private fun <T> withRequestContext(block: () -> T): T {
        val context = Arc.container().requestContext()
        context.activate()
        try {
            return block()
        } finally {
            context.terminate()
        }
    }

    private fun recurrence(scenario: FollowUpDefinitionScenario, principal: PrincipalRef, key: String) =
        DefineInformationRequestRecurrenceCommand(
            requestId = scenario.requestId,
            intervalUnit = InformationRequestRecurrenceUnit.MONTH,
            intervalCount = 1,
            firstDueAt = Instant.now().plus(Duration.ofDays(30)),
            maximumOccurrences = 3,
            access = access(principal),
            precondition = currentPrecondition(scenario),
            idempotencyKey = key,
        )

    private fun refreshRule(scenario: FollowUpDefinitionScenario, principal: PrincipalRef, key: String) =
        DefineInformationRequestRefreshRuleCommand(
            requestId = scenario.requestId,
            requirementKey = "supporting-record",
            leadDays = 30,
            access = access(principal),
            precondition = currentPrecondition(scenario),
            idempotencyKey = key,
        )

    private fun access(principal: PrincipalRef) =
        RequestAccessContext(principal, AuthorizationContext(sessionRef = "follow-up-session"))

    private companion object {
        const val HOLD_MILLIS = 1500L
    }
}
