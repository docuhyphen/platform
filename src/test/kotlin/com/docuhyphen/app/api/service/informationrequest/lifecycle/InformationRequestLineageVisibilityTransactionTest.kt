package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrenceUnit
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestHeaderProjectionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.ReplayedOccurrenceSeries
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.SuccessorReplayScenario
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
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
import java.time.temporal.ChronoUnit
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestLineageVisibilityTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var lineageQueries: InformationRequestLineageQueryService

    @Inject
    lateinit var headerProjection: InformationRequestHeaderProjectionService

    @Inject
    lateinit var appUsers: AppUserService

    @Test
    fun `an origin's follow-up history leaves out a follow-up its caller cannot view and keeps one it can`() {
        val scenario = scenario()
        val kept = supplement(scenario, "kept-supplement")
        val withdrawn = supplement(scenario, "withdrawn-supplement")
        withdrawAccess(withdrawn.successor.id, scenario.contributor)

        assertTrue(permits(scenario.contributor, scenario.sourceId))
        assertTrue(permits(scenario.contributor, kept.successor.id))
        assertFalse(permits(scenario.contributor, withdrawn.successor.id))
        assertEquals(
            listOf(kept.successor.id),
            lineageOf(scenario.sourceId, scenario.contributor).successors.map { it.successorRequestId },
        )
        assertEquals(
            setOf(kept.successor.id, withdrawn.successor.id),
            lineageOf(scenario.sourceId, scenario.manager).successors.map { it.successorRequestId }.toSet(),
        )
    }

    @Test
    fun `a follow-up's history leaves out the source its caller cannot view and names a source its caller can`() {
        val scenario = scenario()
        val created = supplement(scenario, "source-withdrawn")
        withdrawAccess(scenario.sourceId, scenario.contributor)

        assertFalse(permits(scenario.contributor, scenario.sourceId))
        assertTrue(permits(scenario.contributor, created.successor.id))
        assertNull(lineageOf(created.successor.id, scenario.contributor).source)
        val named = lineageOf(created.successor.id, scenario.manager).source
        assertEquals(scenario.sourceId, named?.sourceRequestId)
        assertEquals(REASON, named?.reasonCode)
    }

    @Test
    fun `the next occurrence due time is given only to a caller who can view every occurrence the series created`() {
        val scenario = scenario()
        val series = twoOccurrences(scenario)
        withdrawAccess(series.second.successor.id, scenario.contributor)

        assertTrue(permits(scenario.contributor, scenario.sourceId))
        assertTrue(permits(scenario.contributor, series.first.successor.id))
        assertFalse(permits(scenario.contributor, series.second.successor.id))
        val withheld = lineageOf(scenario.sourceId, scenario.contributor)
        assertEquals(series.recurrence.id, withheld.recurrence?.id)
        assertEquals(listOf(series.first.successor.id), withheld.successors.map { it.successorRequestId })
        assertNull(withheld.nextOccurrenceDueAt)
        assertFalse(withheld.everyOccurrenceCreated)
        val shown = lineageOf(scenario.sourceId, scenario.manager)
        assertEquals(InformationRequestRecurrenceSchedule.dueAt(series.recurrence, 3), shown.nextOccurrenceDueAt)
        assertFalse(shown.everyOccurrenceCreated)
    }

    @Test
    fun `a series is reported complete only to a caller who can view every occurrence it created`() {
        val scenario = scenario()
        val series = twoOccurrences(scenario)
        issue(series.second.successor.id)
        val third = QuarkusTransaction.requiringNew().call {
            scenario.services.followUps.createNextOccurrence(series.firstCommand.copy(idempotencyKey = "occurrence-three"))
        }
        withdrawAccess(third.successor.id, scenario.contributor)

        assertTrue(permits(scenario.contributor, series.second.successor.id))
        assertFalse(permits(scenario.contributor, third.successor.id))
        val complete = lineageOf(scenario.sourceId, scenario.manager)
        assertNull(complete.nextOccurrenceDueAt)
        assertTrue(complete.everyOccurrenceCreated)
        val withheld = lineageOf(scenario.sourceId, scenario.contributor)
        assertNull(withheld.nextOccurrenceDueAt)
        assertFalse(withheld.everyOccurrenceCreated)
        assertEquals(
            listOf(third.successor.id),
            lineageOf(series.second.successor.id, scenario.manager).successors.map { it.successorRequestId },
        )
        assertEquals(
            emptyList<UUID>(),
            lineageOf(series.second.successor.id, scenario.contributor).successors.map { it.successorRequestId },
        )
    }

    @Test
    fun `the follow-up history over HTTP leaves out a follow-up the signed-in caller cannot view`() {
        val scenario = scenario()
        val kept = supplement(scenario, "http-kept-supplement")
        val withdrawn = supplement(scenario, "http-withdrawn-supplement")
        withdrawAccess(withdrawn.successor.id, scenario.contributor)
        assertFalse(permits(scenario.contributor, withdrawn.successor.id))
        val contributor = InformationRequestSignedInCaller.signIn(appUsers, scenario.contributor.id)
        val manager = InformationRequestSignedInCaller.signIn(appUsers, scenario.manager.id)

        val restricted = contributor.authorized().get("/information-requests/${scenario.sourceId}/successors")
        val full = manager.authorized().get("/information-requests/${scenario.sourceId}/successors")
        val direct = contributor.authorized().get("/information-requests/${withdrawn.successor.id}/successors")

        assertEquals(200, restricted.statusCode, restricted.asString())
        assertEquals(
            listOf(kept.successor.id.toString()),
            restricted.jsonPath().getList<String>("successors.successorRequestId"),
        )
        assertFalse(restricted.asString().contains(withdrawn.successor.id.toString()), restricted.asString())
        assertEquals(200, full.statusCode, full.asString())
        assertEquals(
            setOf(kept.successor.id.toString(), withdrawn.successor.id.toString()),
            full.jsonPath().getList<String>("successors.successorRequestId").toSet(),
        )
        assertEquals(403, direct.statusCode, direct.asString())
    }

    @Test
    fun `a superseded request names its replacement only to a caller who can view the replacement`() {
        val scenario = scenario(submitted = false)
        val replacement = QuarkusTransaction.requiringNew().call {
            scenario.services.successors.create(
                follow(scenario, InformationRequestLineageKind.SUPERSEDING, "superseding-replacement"),
            )
        }
        withdrawAccess(replacement.successor.id, scenario.contributor)

        assertTrue(permits(scenario.contributor, scenario.sourceId))
        assertFalse(permits(scenario.contributor, replacement.successor.id))
        assertTrue(permits(scenario.manager, replacement.successor.id))
        val superseded = QuarkusTransaction.requiringNew().call {
            requireNotNull(requestRepository.findById(scenario.sourceId))
        }
        assertEquals(InformationRequestState.SUPERSEDED, superseded.state)
        assertEquals(replacement.successor.id, superseded.supersededByRequestId)
        assertNull(headerOf(superseded, scenario.contributor).supersededByRequestId)
        assertEquals(replacement.successor.id, headerOf(superseded, scenario.manager).supersededByRequestId)
        assertNull(lineageOf(scenario.sourceId, scenario.contributor).successors.firstOrNull())

        val contributor = InformationRequestSignedInCaller.signIn(appUsers, scenario.contributor.id)
        val manager = InformationRequestSignedInCaller.signIn(appUsers, scenario.manager.id)
        val restricted = contributor.authorized().get("/information-requests/${scenario.sourceId}")
        val full = manager.authorized().get("/information-requests/${scenario.sourceId}")

        assertEquals(200, restricted.statusCode, restricted.asString())
        assertNull(restricted.jsonPath().getString("supersededByRequestId"), restricted.asString())
        assertFalse(restricted.asString().contains(replacement.successor.id.toString()), restricted.asString())
        assertEquals(200, full.statusCode, full.asString())
        assertEquals(replacement.successor.id.toString(), full.jsonPath().getString("supersededByRequestId"))
    }

    private fun scenario(submitted: Boolean = true): SuccessorReplayScenario {
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
        if (submitted) submitWhole(services, fixture)
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

    private fun supplement(scenario: SuccessorReplayScenario, key: String): InformationRequestSuccessorResult =
        QuarkusTransaction.requiringNew().call {
            scenario.services.successors.create(follow(scenario, InformationRequestLineageKind.SUPPLEMENT, key))
        }

    private fun follow(scenario: SuccessorReplayScenario, kind: InformationRequestLineageKind, key: String) =
        CreateInformationRequestSuccessorCommand(
            sourceRequestId = scenario.sourceId,
            kind = kind,
            reasonCode = REASON,
            access = access(scenario.manager),
            precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.sourceId)),
            idempotencyKey = key,
        )

    private fun lineageOf(requestId: UUID, principal: PrincipalRef): InformationRequestLineageView =
        QuarkusTransaction.requiringNew().call { lineageQueries.lineage(requestId, access(principal)) }

    private fun headerOf(request: InformationRequest, principal: PrincipalRef): InformationRequestDto =
        QuarkusTransaction.requiringNew().call { headerProjection.headerOf(request, access(principal)) }

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
                AuthorizationContext(sessionRef = "lineage-session"),
            ) is Decision.Allow
        }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

    private fun access(principal: PrincipalRef) =
        RequestAccessContext(principal, AuthorizationContext(sessionRef = "lineage-session"))

    private companion object {
        const val REASON = "additional-information"
    }
}
