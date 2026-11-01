package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.InformationRequestSuccessorResource
import com.docuhyphen.app.api.resource.model.CreateInformationRequestSuccessorRequest
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestHeaderProjectionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.ReplayedOccurrenceSeries
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.SuccessorReplayScenario
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.SuccessorReplayWrites
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestSuccessorReplayAuthorizationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var headerProjection: InformationRequestHeaderProjectionService

    @Test
    fun `an explicit follow-up is not replayed once its caller can no longer view the follow-up`() {
        val scenario = scenario()
        val sourceETag = aggregateETag(scenario.sourceId)
        val command = supplement(scenario, "supplement-before-removal", sourceETag)
        val created = QuarkusTransaction.requiringNew().call { scenario.services.successors.create(command) }
        val recorded = writesOf(scenario)

        withdrawAccess(created.successor.id, scenario.manager)

        assertTrue(permits(scenario.manager, scenario.sourceId))
        assertTrue(!permits(scenario.manager, created.successor.id))
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { scenario.services.successors.create(command) }
        }
        val response = QuarkusTransaction.requiringNew().call {
            InformationRequestSuccessorResource(
                scenario.services.successors,
                scenario.services.lineageQueries,
                accessFactory(access(scenario.manager)),
                headerProjection,
            ).create(
                scenario.sourceId.toString(),
                CreateInformationRequestSuccessorRequest(InformationRequestLineageKind.SUPPLEMENT, null, null, REASON),
                sourceETag,
                command.idempotencyKey,
            )
        }
        assertEquals(403, response.status)
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `a recurrence occurrence is not replayed once its caller can no longer view that occurrence or the request it followed`() {
        val scenario = scenario()
        val series = twoOccurrences(scenario)
        val recorded = writesOf(scenario)

        withdrawAccess(series.first.successor.id, scenario.manager)

        assertTrue(permits(scenario.manager, scenario.sourceId))
        assertTrue(permits(scenario.manager, series.second.successor.id))
        assertTrue(!permits(scenario.manager, series.first.successor.id))
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { scenario.services.followUps.createNextOccurrence(series.firstCommand) }
        }
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew()
                .call { scenario.services.followUps.createNextOccurrence(series.secondCommand) }
        }
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `a replayed occurrence names the request it followed and needs no view of later occurrences`() {
        val scenario = scenario()
        val series = twoOccurrences(scenario)

        val firstReplay = QuarkusTransaction.requiringNew()
            .call { scenario.services.followUps.createNextOccurrence(series.firstCommand) }
        val secondReplay = QuarkusTransaction.requiringNew()
            .call { scenario.services.followUps.createNextOccurrence(series.secondCommand) }

        assertEquals(series.first.successor.id, firstReplay.successor.id)
        assertEquals(scenario.sourceId, firstReplay.source.id)
        assertEquals(scenario.sourceId, firstReplay.lineage.sourceRequestId)
        assertEquals(series.first.successorETag, firstReplay.successorETag)
        assertEquals(series.second.successor.id, secondReplay.successor.id)
        assertEquals(series.first.successor.id, secondReplay.source.id)
        assertEquals(series.first.successor.id, secondReplay.lineage.sourceRequestId)

        val recorded = writesOf(scenario)
        withdrawAccess(series.second.successor.id, scenario.manager)

        assertTrue(!permits(scenario.manager, series.second.successor.id))
        val replayAfterWithdrawal = QuarkusTransaction.requiringNew()
            .call { scenario.services.followUps.createNextOccurrence(series.firstCommand) }
        assertEquals(series.first.successor.id, replayAfterWithdrawal.successor.id)
        assertEquals(scenario.sourceId, replayAfterWithdrawal.source.id)
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `the next occurrence due time counts every occurrence of the series`() {
        val scenario = scenario()
        val series = twoOccurrences(scenario)
        val lineage = {
            QuarkusTransaction.requiringNew()
                .call { scenario.services.lineageQueries.lineage(scenario.sourceId, access(scenario.manager)) }
        }

        assertEquals(
            InformationRequestRecurrenceSchedule.dueAt(series.recurrence, 3),
            lineage().nextOccurrenceDueAt,
        )

        issue(series.second.successor.id)
        QuarkusTransaction.requiringNew().call {
            scenario.services.followUps.createNextOccurrence(series.firstCommand.copy(idempotencyKey = "occurrence-three"))
        }

        assertNull(lineage().nextOccurrenceDueAt)
    }

    @Test
    fun `a next occurrence is not created by a caller who cannot supersede the latest occurrence`() {
        val scenario = scenario()
        val recurrence = QuarkusTransaction.requiringNew().call {
            scenario.services.followUps.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = scenario.sourceId,
                    intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                    intervalCount = 1,
                    firstDueAt = Instant.now().minus(Duration.ofDays(90)),
                    maximumOccurrences = 3,
                    access = access(scenario.manager),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.sourceId)),
                    idempotencyKey = "recurrence",
                ),
            ).recurrence
        }
        val firstCommand = CreateNextInformationRequestOccurrenceCommand(
            scenario.sourceId,
            recurrence.id,
            access(scenario.manager),
            "occurrence-one"
        )
        val first =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.createNextOccurrence(firstCommand) }
        issue(first.successor.id)
        withdrawAccess(first.successor.id, scenario.manager)
        val recorded = writesOf(scenario)

        assertTrue(permits(scenario.manager, scenario.sourceId))
        assertTrue(!permits(scenario.manager, first.successor.id))
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.followUps.createNextOccurrence(firstCommand.copy(idempotencyKey = "occurrence-two"))
            }
        }
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `a refresh is not replayed once its caller can no longer view the refreshing request`() {
        val scenario = scenario()
        val rule = QuarkusTransaction.requiringNew().call {
            scenario.services.followUps.defineRefreshRule(
                DefineInformationRequestRefreshRuleCommand(
                    requestId = scenario.sourceId,
                    requirementKey = "supporting-record",
                    leadDays = 30,
                    access = access(scenario.manager),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.sourceId)),
                    idempotencyKey = "refresh-rule",
                ),
            ).refreshRule
        }
        val command =
            RefreshInformationRequestCommand(scenario.sourceId, rule.id, access(scenario.manager), "refresh-once")
        val refreshed = QuarkusTransaction.requiringNew().call { scenario.services.followUps.refresh(command) }
        val recorded = writesOf(scenario)

        withdrawAccess(refreshed.successor.id, scenario.manager)

        assertTrue(permits(scenario.manager, scenario.sourceId))
        assertTrue(!permits(scenario.manager, refreshed.successor.id))
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.refresh(command) }
        }
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `a caller who keeps a retained read of the follow-up replays it with the follow-up's current validator without writing again`() {
        val scenario = scenario()
        val command = supplement(scenario, "supplement-kept-readable")
        val created = QuarkusTransaction.requiringNew().call { scenario.services.successors.create(command) }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET aggregate_revision = aggregate_revision + 1 WHERE id = ?",
                created.successor.id
            )
        }
        val recorded = writesOf(scenario)

        withdrawAccess(created.successor.id, scenario.manager)
        dataSource.connection.use { connection ->
            share(
                connection,
                created.successor.id,
                scenario.manager,
                "RECORD_OWNER"
            )
        }

        assertTrue(permits(scenario.manager, created.successor.id))
        val replayed = QuarkusTransaction.requiringNew().call { scenario.services.successors.create(command) }
        assertEquals(created.successor.id, replayed.successor.id)
        assertEquals(created.lineage.id, replayed.lineage.id)
        assertNotEquals(created.successorETag, replayed.successorETag)
        assertEquals(aggregateETag(created.successor.id), replayed.successorETag)
        assertEquals(recorded, writesOf(scenario))
    }

    @Test
    fun `an idempotency key replays a follow-up only for the caller who used it`() {
        val scenario = scenario()
        val command = supplement(scenario, "shared-supplement-key")
        QuarkusTransaction.requiringNew().call { scenario.services.successors.create(command) }
        val recorded = writesOf(scenario)

        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                scenario.services.successors.create(command.copy(access = access(scenario.contributor)))
            }
        }
        assertEquals(recorded, writesOf(scenario))
    }

    private fun scenario(): SuccessorReplayScenario {
        val managerId = UUID.randomUUID()
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(
                connection,
                evidenceExpiresOn = LocalDate.now(ZoneOffset.UTC).plusDays(10)
            ).also { fixture ->
                val ownerPartyId = UUID.randomUUID()
                val managerPartyId = UUID.randomUUID()
                fixture.insertPartyForUser(ownerPartyId, "DECISION_MAKER", fixture.template.userId)
                fixture.insertActingParty(managerPartyId, "DECISION_MAKER", managerId)
                listOf(
                    Triple(fixture.contributorPartyId, fixture.contributorUserId, "CONTRIBUTOR"),
                    Triple(fixture.attestorPartyId, fixture.attestorUserId, "ATTESTOR"),
                    Triple(ownerPartyId, fixture.template.userId, "DECISION_MAKER"),
                    Triple(managerPartyId, managerId, "DECISION_MAKER"),
                ).forEach { (partyId, userId, role) ->
                    val shareId = share(connection, fixture.requestId, PrincipalRef.user(userId), role)
                    execute(
                        connection,
                        "UPDATE information_request_party SET share_id = ? WHERE id = ?",
                        shareId,
                        partyId
                    )
                }
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
        val services = runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        submitWhole(services, fixture)
        return SuccessorReplayScenario(fixture, services, PrincipalRef.user(managerId))
    }

    private fun submitWhole(services: InformationRequestRuntimeServices, fixture: SubmissionRuntimeSqlFixture) {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access(PrincipalRef.user(fixture.attestorUserId)),
                    precondition = CommandPrecondition.ExpectedRevision(
                        QuarkusTransaction.requiringNew().call {
                            val request = requireNotNull(requestRepository.findById(fixture.requestId))
                            InformationRequestETag.submissionOf(
                                null,
                                runtime.contentCollector.collect(request, null).contentHash
                            )
                        },
                    ),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        QuarkusTransaction.requiringNew().run {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = access(PrincipalRef.user(fixture.contributorUserId)),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "submit-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun twoOccurrences(scenario: SuccessorReplayScenario): ReplayedOccurrenceSeries {
        val recurrence = QuarkusTransaction.requiringNew().call {
            scenario.services.followUps.defineRecurrence(
                DefineInformationRequestRecurrenceCommand(
                    requestId = scenario.sourceId,
                    intervalUnit = InformationRequestRecurrenceUnit.MONTH,
                    intervalCount = 1,
                    firstDueAt = Instant.now().minus(Duration.ofDays(90)).truncatedTo(ChronoUnit.MILLIS),
                    maximumOccurrences = 3,
                    access = access(scenario.manager),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.sourceId)),
                    idempotencyKey = "recurrence",
                ),
            ).recurrence
        }
        val firstCommand = CreateNextInformationRequestOccurrenceCommand(
            scenario.sourceId,
            recurrence.id,
            access(scenario.manager),
            "occurrence-one"
        )
        val first =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.createNextOccurrence(firstCommand) }
        issue(first.successor.id)
        val secondCommand = firstCommand.copy(idempotencyKey = "occurrence-two")
        val second =
            QuarkusTransaction.requiringNew().call { scenario.services.followUps.createNextOccurrence(secondCommand) }
        return ReplayedOccurrenceSeries(recurrence, firstCommand, first, secondCommand, second)
    }

    private fun supplement(
        scenario: SuccessorReplayScenario,
        key: String,
        sourceETag: String = aggregateETag(scenario.sourceId)
    ) =
        CreateInformationRequestSuccessorCommand(
            sourceRequestId = scenario.sourceId,
            kind = InformationRequestLineageKind.SUPPLEMENT,
            reasonCode = REASON,
            access = access(scenario.manager),
            precondition = CommandPrecondition.ExpectedRevision(sourceETag),
            idempotencyKey = key,
        )

    private fun issue(requestId: UUID) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'ISSUED', issued_at = now() WHERE id = ?",
                requestId
            )
        }
    }

    private fun withdrawAccess(requestId: UUID, principal: PrincipalRef) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                UPDATE share SET status = 'REVOKED', revoked_at = now()
                WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ? AND principal_kind = 'USER' AND principal_id = ? AND status = 'ACTIVE'
                """.trimIndent(),
                requestId,
                principal.id,
            )
            execute(
                connection,
                "UPDATE information_request_party SET active = FALSE WHERE information_request_id = ? AND principal_kind = 'USER' AND principal_id = ?",
                requestId,
                principal.id,
            )
        }
    }

    private fun share(connection: Connection, requestId: UUID, principal: PrincipalRef, role: String): UUID {
        val shareId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'INFORMATION_REQUEST', ?, ?, ?, ?, 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            shareId,
            requestId,
            principal.kind.name,
            principal.id,
            role,
        )
        return shareId
    }

    private fun permits(principal: PrincipalRef, requestId: UUID): Boolean =
        QuarkusTransaction.requiringNew().call {
            authorizationService.authorize(
                principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                AuthorizationContext(sessionRef = "follow-up-session"),
            ) is Decision.Allow
        }

    private fun writesOf(scenario: SuccessorReplayScenario): SuccessorReplayWrites =
        dataSource.connection.use { connection ->
            SuccessorReplayWrites(
                requests = queryInt(
                    connection,
                    "SELECT count(*) FROM information_request WHERE exchange_id = ?",
                    scenario.exchangeId
                ),
                lineages = queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_lineage l JOIN information_request r ON r.id = l.successor_request_id WHERE r.exchange_id = ?",
                    scenario.exchangeId,
                ),
                carryForwards = queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_carry_forward c JOIN information_request r ON r.id = c.information_request_id WHERE r.exchange_id = ?",
                    scenario.exchangeId,
                ),
                transitions = queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_transition t JOIN information_request r ON r.id = t.information_request_id WHERE r.exchange_id = ?",
                    scenario.exchangeId,
                ),
                receipts = queryInt(
                    connection,
                    "SELECT count(*) FROM command_receipt WHERE actor_id IN (?, ?)",
                    scenario.manager.id,
                    scenario.contributor.id
                ),
            )
        }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

    private fun accessFactory(access: RequestAccessContext) =
        mock<InformationRequestAccessContextFactory> { on { currentAuthenticated() } doReturn access }

    private fun access(principal: PrincipalRef) =
        RequestAccessContext(principal, AuthorizationContext(sessionRef = "follow-up-session"))

    private companion object {
        const val REASON = "additional-information"
    }
}
