package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineageKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CreateInformationRequestSuccessorCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestSuccessorResult
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.model.SuccessorReplayScenario
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.response.Response
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.sql.Connection
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestRelatedRequestRecordTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var appUsers: AppUserService

    @Inject
    lateinit var exports: InformationRequestRecordExportService

    @Test
    fun `the audit history names a replacement only to a caller who may view it and keeps every event for both`() {
        val scenario = scenario(submitted = false)
        val replacement = follow(scenario, InformationRequestLineageKind.SUPERSEDING, "audit-superseding")
        withdrawAccess(replacement.successor.id, scenario.manager)

        assertFalse(permits(scenario.manager, replacement.successor.id))
        assertTrue(permits(scenario.manager, scenario.sourceId))
        assertTrue(permits(ownerOf(scenario), replacement.successor.id))
        val manager = signIn(scenario.manager)
        val restricted = manager.authorized().get("/information-requests/${scenario.sourceId}/audit-events")
        val full = signIn(ownerOf(scenario)).authorized().get("/information-requests/${scenario.sourceId}/audit-events")
        val direct = manager.authorized().get("/information-requests/${replacement.successor.id}")

        assertEquals(403, direct.statusCode, direct.asString())
        assertEquals(200, restricted.statusCode, restricted.asString())
        assertEquals(200, full.statusCode, full.asString())
        assertFalse(restricted.asString().contains(replacement.successor.id.toString()), restricted.asString())
        assertEquals(eventIds(full), eventIds(restricted))
        listOf(
            SUCCESSOR_EVENT to "successorRequestId",
            SUPERSEDE_EVENT to "supersededByRequestId"
        ).forEach { (type, key) ->
            val withheld = eventsOf(restricted, type).single()
            val shown = eventsOf(full, type).single()
            assertEquals(replacement.successor.id.toString(), payloadOf(shown)[key], type)
            assertFalse(key in payloadOf(withheld), type)
            assertEquals(payloadOf(shown) - key, payloadOf(withheld), type)
            assertEquals(withheldCountOf(shown) + 1, withheldCountOf(withheld), type)
        }
    }

    @Test
    fun `the audit history names a follow-up its caller may view and withholds one it may not`() {
        val scenario = scenario()
        val kept = follow(scenario, InformationRequestLineageKind.SUPPLEMENT, "audit-kept-supplement")
        val withdrawn = follow(scenario, InformationRequestLineageKind.SUPPLEMENT, "audit-withdrawn-supplement")
        withdrawAccess(withdrawn.successor.id, scenario.manager)

        assertTrue(permits(scenario.manager, kept.successor.id))
        assertFalse(permits(scenario.manager, withdrawn.successor.id))
        val restricted =
            signIn(scenario.manager).authorized().get("/information-requests/${scenario.sourceId}/audit-events")
        val full = signIn(ownerOf(scenario)).authorized().get("/information-requests/${scenario.sourceId}/audit-events")

        assertEquals(200, restricted.statusCode, restricted.asString())
        assertFalse(restricted.asString().contains(withdrawn.successor.id.toString()), restricted.asString())
        val named = eventsOf(restricted, SUCCESSOR_EVENT).map { payloadOf(it)["successorRequestId"] }
        assertEquals(listOf(kept.successor.id.toString()), named.filterNotNull())
        assertEquals(2, named.size)
        val shown = eventsOf(full, SUCCESSOR_EVENT).associateBy { payloadOf(it)["successorRequestId"] }
        val withheld = eventsOf(restricted, SUCCESSOR_EVENT).single { "successorRequestId" !in payloadOf(it) }
        val withheldInFull = requireNotNull(shown[withdrawn.successor.id.toString()])
        assertEquals(withheldInFull["eventId"], withheld["eventId"])
        assertEquals(withheldCountOf(withheldInFull) + 1, withheldCountOf(withheld))
        assertEquals(
            withheldCountOf(requireNotNull(shown[kept.successor.id.toString()])), withheldCountOf(
                eventsOf(
                    restricted,
                    SUCCESSOR_EVENT
                ).single { payloadOf(it)["successorRequestId"] == kept.successor.id.toString() },
            )
        )
    }

    @Test
    fun `an export made after the caller lost the replacement is frozen without it and its hash covers what was stored`() {
        val scenario = scenario(submitted = false)
        val replacement = follow(scenario, InformationRequestLineageKind.SUPERSEDING, "export-superseding")
        withdrawAccess(replacement.successor.id, scenario.manager)
        val manager = signIn(scenario.manager)
        val owner = signIn(ownerOf(scenario))

        val exportId = createExport(manager, scenario.sourceId)
        val read = manager.authorized().get("/information-requests/${scenario.sourceId}/record-exports/$exportId")
        val listed = manager.authorized().get("/information-requests/${scenario.sourceId}/record-exports")
        val readByOwner = owner.authorized().get("/information-requests/${scenario.sourceId}/record-exports/$exportId")
        val ownExport = owner.authorized()
            .get("/information-requests/${scenario.sourceId}/record-exports/${createExport(owner, scenario.sourceId)}")

        assertEquals(200, read.statusCode, read.asString())
        assertFalse(read.asString().contains(replacement.successor.id.toString()), read.asString())
        assertNull(read.jsonPath().getString("content.request.supersededByRequestId"))
        assertTrue(read.jsonPath().getBoolean("content.request.supersededByRequestRedacted"))
        assertEquals("SUPERSEDED", read.jsonPath().getString("content.request.state"))
        assertEquals(0, read.jsonPath().getInt("withheldRelatedRequestCount"))
        assertTrue(read.jsonPath().getBoolean("verified"))
        assertEquals(200, listed.statusCode, listed.asString())
        assertFalse(listed.asString().contains(replacement.successor.id.toString()), listed.asString())
        val stored = storedContent(exportId)
        assertFalse(stored.contains(replacement.successor.id.toString()))
        assertEquals(sha256(stored), storedHash(exportId))
        assertEquals(storedHash(exportId), read.jsonPath().getString("contentHash"))
        assertEquals(200, readByOwner.statusCode, readByOwner.asString())
        assertFalse(readByOwner.asString().contains(replacement.successor.id.toString()), readByOwner.asString())
        assertEquals(200, ownExport.statusCode, ownExport.asString())
        assertEquals(
            replacement.successor.id.toString(),
            ownExport.jsonPath().getString("content.request.supersededByRequestId")
        )
        assertEquals(0, ownExport.jsonPath().getInt("withheldRelatedRequestCount"))
    }

    @Test
    fun `a retained export read after the caller lost the replacement leaves it out without changing the stored record`() {
        val scenario = scenario(submitted = false)
        val replacement = follow(scenario, InformationRequestLineageKind.SUPERSEDING, "retained-superseding")
        assertTrue(permits(scenario.manager, replacement.successor.id))
        val exportId = QuarkusTransaction.requiringNew().call {
            exports.create(
                CreateInformationRequestRecordExportCommand(
                    requestId = scenario.sourceId,
                    access = access(scenario.manager),
                    transferRegion = null,
                    idempotencyKey = "retained-export",
                ),
            ).export.id.toString()
        }
        val storedBefore = storedContent(exportId)
        withdrawAccess(replacement.successor.id, scenario.manager)

        assertFalse(permits(scenario.manager, replacement.successor.id))
        val read = signIn(scenario.manager).authorized()
            .get("/information-requests/${scenario.sourceId}/record-exports/$exportId")
        val readByOwner = signIn(ownerOf(scenario)).authorized()
            .get("/information-requests/${scenario.sourceId}/record-exports/$exportId")

        assertTrue(storedBefore.contains(replacement.successor.id.toString()))
        assertEquals(200, read.statusCode, read.asString())
        assertFalse(read.asString().contains(replacement.successor.id.toString()), read.asString())
        assertTrue(read.jsonPath().getBoolean("content.request.supersededByRequestRedacted"))
        assertEquals(1, read.jsonPath().getInt("withheldRelatedRequestCount"))
        assertTrue(read.jsonPath().getBoolean("verified"))
        assertEquals(sha256(storedBefore), read.jsonPath().getString("contentHash"))
        assertEquals(storedBefore, storedContent(exportId))
        assertEquals(sha256(storedBefore), storedHash(exportId))
        assertEquals(200, readByOwner.statusCode, readByOwner.asString())
        assertEquals(
            replacement.successor.id.toString(),
            readByOwner.jsonPath().getString("content.request.supersededByRequestId"),
        )
        assertEquals(0, readByOwner.jsonPath().getInt("withheldRelatedRequestCount"))
    }

    private fun createExport(caller: InformationRequestSignedInCaller, requestId: UUID): String {
        val created = caller.command("export-${UUID.randomUUID()}").body("{}")
            .post("/information-requests/$requestId/record-exports")
        assertEquals(201, created.statusCode, created.asString())
        return created.jsonPath().getString("id")
    }

    private fun eventIds(response: Response): Set<String> =
        response.jsonPath().getList<Map<String, Any?>>("items").map { it["eventId"].toString() }.toSet()

    private fun eventsOf(response: Response, eventTypeKey: String): List<Map<String, Any?>> =
        response.jsonPath().getList<Map<String, Any?>>("items").filter { it["eventTypeKey"] == eventTypeKey }

    @Suppress("UNCHECKED_CAST")
    private fun payloadOf(event: Map<String, Any?>): Map<String, String> = event["payload"] as Map<String, String>

    private fun withheldCountOf(event: Map<String, Any?>): Int = (event["withheldKeyCount"] as Number).toInt()

    private fun storedContent(exportId: String): String = storedColumn("content_json", exportId)

    private fun storedHash(exportId: String): String = storedColumn("content_hash", exportId)

    private fun storedColumn(column: String, exportId: String): String =
        dataSource.connection.use { connection ->
            requireNotNull(
                queryString(
                    connection,
                    "SELECT $column FROM information_request_record_export WHERE id = ?",
                    UUID.fromString(exportId),
                ),
            )
        }

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun signIn(principal: PrincipalRef): InformationRequestSignedInCaller =
        InformationRequestSignedInCaller.signIn(appUsers, principal.id)

    private fun ownerOf(scenario: SuccessorReplayScenario): PrincipalRef =
        PrincipalRef.user(scenario.fixture.template.userId)

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

    private fun follow(
        scenario: SuccessorReplayScenario,
        kind: InformationRequestLineageKind,
        key: String,
    ): InformationRequestSuccessorResult =
        QuarkusTransaction.requiringNew().call {
            scenario.services.successors.create(
                CreateInformationRequestSuccessorCommand(
                    sourceRequestId = scenario.sourceId,
                    kind = kind,
                    reasonCode = REASON,
                    access = access(scenario.manager),
                    precondition = CommandPrecondition.ExpectedRevision(aggregateETag(scenario.sourceId)),
                    idempotencyKey = key,
                ),
            )
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
                AuthorizationContext(sessionRef = "related-record-session"),
            ) is Decision.Allow
        }

    private fun aggregateETag(requestId: UUID): String =
        QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(requestId))) }

    private fun access(principal: PrincipalRef) =
        RequestAccessContext(principal, AuthorizationContext(sessionRef = "related-record-session"))

    private companion object {
        const val REASON = "additional-information"
        const val SUCCESSOR_EVENT = "information_request.request.successor"
        const val SUPERSEDE_EVENT = "information_request.request.supersede"
    }
}
