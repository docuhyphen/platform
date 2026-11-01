package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestAuditSearch
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordTransferVerdict
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.AuditTargetHistoryService
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockRecorder
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordAssembler
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordStorageLocationPolicy
import com.docuhyphen.app.api.service.recordpreservation.RecordTransferPolicy
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.narayana.jta.TransactionExceptionResult
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestAuditExportTransactionTest
{
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var transitionRepository: InformationRequestTransitionRepository

    @Inject
    lateinit var clockRepository: InformationRequestClockRepository

    @Inject
    lateinit var clockEventRepository: InformationRequestClockEventRepository

    @Inject
    lateinit var clockPolicies: InformationRequestClockPolicyService

    @Inject
    lateinit var clockRecorder: InformationRequestClockRecorder

    @Inject
    lateinit var history: AuditTargetHistoryService

    @Inject
    lateinit var auditRecorder: AuditRecorder

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    @Inject
    lateinit var assembler: InformationRequestRecordAssembler

    @Inject
    lateinit var exportRepository: InformationRequestRecordExportRepository

    @Inject
    lateinit var recordDisposals: RecordDisposalService

    @Inject
    lateinit var storageLocations: RecordStorageLocationPolicy

    @Inject
    lateinit var transfers: RecordTransferPolicy

    @Inject
    lateinit var requirementRepository: InformationRequestRequirementRepository

    @Inject
    lateinit var readAudit: InformationRequestReadAudit

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Test
    fun `a request's audit history is classified, withholds payload keys outside the allow-list, and pages`()
    {
        val fixture = startedFixture()
        QuarkusTransaction.requiringNew().run {
            auditRecorder.record(
                AuditEventDraft(
                    owner = AuditOwnerScope.Organization(fixture.template.organizationId),
                    eventTypeKey = AuditEventType.INFORMATION_REQUEST_EVIDENCE_DOWNLOAD.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = fixture.template.userId,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = "INFORMATION_REQUEST",
                    targetId = fixture.requestId.toString(),
                    payload = mapOf(
                        "requirementId" to UUID.randomUUID().toString(),
                        "declaredFileName" to "private-record.pdf"
                    ),
                ),
            )
        }
        val audit = auditService(fixture)

        val page = QuarkusTransaction.requiringNew()
            .call { audit.events(fixture.requestId, owner(fixture), InformationRequestAuditSearch()) }

        assertTrue(page.events.any { it.eventClass == "clock" && it.record.eventTypeKey == "information_request.clock.start" })
        val download =
            page.events.single { it.record.eventTypeKey == AuditEventType.INFORMATION_REQUEST_EVIDENCE_DOWNLOAD.key }
        assertEquals("evidence", download.eventClass)
        assertEquals(setOf("requirementId"), download.payload.keys)
        assertEquals(1, download.withheldKeyCount)
        val clockOnly = QuarkusTransaction.requiringNew().call {
            audit.events(
                fixture.requestId,
                owner(fixture),
                InformationRequestAuditSearch(eventClass = "clock", limit = 1)
            )
        }
        assertEquals(1, clockOnly.events.size)
        assertTrue(clockOnly.total >= 1)
        val searched = QuarkusTransaction.requiringNew()
            .call { audit.search(InformationRequestAuditSearch(requestId = fixture.requestId)) }
        assertEquals(page.total, searched.total)
    }

    @Test
    fun `a record export withholds confidential Requirements from an unverified caller and reading disclosed confidential content needs recent verification`() {
        val fixture = confidentialFixture()
        val exports = exportService(fixture, transfers, authorizationService)
        val unverified = owner(fixture)
        val verified = RequestAccessContext(
            PrincipalRef.user(fixture.template.userId),
            AuthorizationContext(sessionRef = "owner", mfaSatisfied = true)
        )

        val withheld = QuarkusTransaction.requiringNew().call {
            exports.create(
                CreateInformationRequestRecordExportCommand(
                    fixture.requestId,
                    unverified,
                    null,
                    "export-withheld"
                )
            )
        }
        val withheldRead =
            QuarkusTransaction.requiringNew().call { exports.read(fixture.requestId, withheld.export.id, unverified) }
        val disclosed = QuarkusTransaction.requiringNew().call {
            exports.create(
                CreateInformationRequestRecordExportCommand(
                    fixture.requestId,
                    verified,
                    null,
                    "export-disclosed"
                )
            )
        }
        val refusal = assertThrows(StepUpRequiredException::class.java) {
            QuarkusTransaction.requiringNew().call { exports.read(fixture.requestId, disclosed.export.id, unverified) }
        }
        val disclosedRead =
            QuarkusTransaction.requiringNew().call { exports.read(fixture.requestId, disclosed.export.id, verified) }

        val hidden = documentItem(Json.parseToJsonElement(requireNotNull(withheldRead.content)).jsonObject)
        assertEquals("true", hidden.getValue("redacted").jsonPrimitive.content)
        assertFalse(hidden.containsKey("itemHashSha256"))
        assertFalse(withheldRead.containsConfidentialContent)
        assertTrue(disclosed.containsConfidentialContent)
        assertEquals(401, refusal.response.status)
        val shown = documentItem(Json.parseToJsonElement(requireNotNull(disclosedRead.content)).jsonObject)
        assertFalse(shown.containsKey("redacted"))
        assertEquals("c".repeat(64), shown.getValue("itemHashSha256").jsonPrimitive.content)
    }

    @Test
    fun `owner audit search lists the owner's request, Template, clock policy, and privacy events and nothing another owner recorded`() {
        val fixture = startedFixture()
        val other = startedFixture()
        val owner = AuditOwnerScope.Organization(fixture.template.organizationId)
        val otherOwner = AuditOwnerScope.Organization(other.template.organizationId)
        val ownTargets = mapOf(
            "INFORMATION_REQUEST_TEMPLATE_DEFINITION" to UUID.randomUUID(),
            "INFORMATION_REQUEST_CLOCK_POLICY" to UUID.randomUUID(),
            "INFORMATION_REQUEST_PRIVACY_REQUEST" to UUID.randomUUID(),
        )
        val eventTypes = mapOf(
            "INFORMATION_REQUEST_TEMPLATE_DEFINITION" to AuditEventType.INFORMATION_REQUEST_TEMPLATE_PUBLISH,
            "INFORMATION_REQUEST_CLOCK_POLICY" to AuditEventType.INFORMATION_REQUEST_CLOCK_POLICY_PUBLISH,
            "INFORMATION_REQUEST_PRIVACY_REQUEST" to AuditEventType.INFORMATION_REQUEST_PRIVACY_RECORD,
        )
        val foreignTargets = mutableListOf<UUID>()
        ownTargets.forEach { (targetType, targetId) ->
            recordOwnerEvent(owner, eventTypes.getValue(targetType), targetType, targetId, fixture.template.userId)
            val foreign = UUID.randomUUID().also(foreignTargets::add)
            recordOwnerEvent(otherOwner, eventTypes.getValue(targetType), targetType, foreign, other.template.userId)
        }
        val platformTemplate = UUID.randomUUID().also(foreignTargets::add)
        recordOwnerEvent(
            AuditOwnerScope.Platform,
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_PUBLISH,
            "INFORMATION_REQUEST_TEMPLATE_DEFINITION",
            platformTemplate,
            fixture.template.userId,
        )

        val page = QuarkusTransaction.requiringNew()
            .call { auditService(fixture).search(InformationRequestAuditSearch(limit = 500)) }
        val scoped = QuarkusTransaction.requiringNew().call {
            auditService(fixture).search(InformationRequestAuditSearch(requestId = fixture.requestId, limit = 500))
        }

        val listed = page.events.map { it.record.targetType to it.record.targetId }.toSet()
        ownTargets.forEach { (targetType, targetId) ->
            assertTrue(
                (targetType to targetId.toString()) in listed,
                targetType
            )
        }
        assertTrue(("INFORMATION_REQUEST" to fixture.requestId.toString()) in listed)
        assertTrue(page.events.none { event -> foreignTargets.any { it.toString() == event.record.targetId } })
        assertTrue(page.events.none { it.record.targetId == other.requestId.toString() })
        val privacyEvent = page.events.single {
            it.record.targetId == ownTargets.getValue("INFORMATION_REQUEST_PRIVACY_REQUEST").toString()
        }
        assertEquals("policy.basis", privacyEvent.payload["policyBasisKey"])
        assertTrue(scoped.events.isNotEmpty())
        assertTrue(scoped.events.all { it.record.targetType == "INFORMATION_REQUEST" && it.record.targetId == fixture.requestId.toString() })
    }

    @Test
    fun `a person's owner audit search lists only that person's events and nothing another person or a same-id organization recorded`() {
        val fixture = startedFixture()
        val person = UUID.randomUUID()
        val otherPerson = UUID.randomUUID()
        val ownTargets = mutableSetOf<String>()
        listOf(
            AuditEventType.INFORMATION_REQUEST_TEMPLATE_PUBLISH to "INFORMATION_REQUEST_TEMPLATE_DEFINITION",
            AuditEventType.INFORMATION_REQUEST_CLOCK_POLICY_PUBLISH to "INFORMATION_REQUEST_CLOCK_POLICY",
            AuditEventType.INFORMATION_REQUEST_PRIVACY_RECORD to "INFORMATION_REQUEST_PRIVACY_REQUEST",
        ).forEach { (eventType, targetType) ->
            val own = UUID.randomUUID().also { ownTargets += it.toString() }
            recordOwnerEvent(AuditOwnerScope.Personal(person), eventType, targetType, own, person)
            recordOwnerEvent(
                AuditOwnerScope.Personal(otherPerson),
                eventType,
                targetType,
                UUID.randomUUID(),
                otherPerson
            )
            recordOwnerEvent(AuditOwnerScope.Organization(person), eventType, targetType, UUID.randomUUID(), person)
        }

        val page = QuarkusTransaction.requiringNew().call {
            auditService(fixture, InformationRequestOwnerRef(InformationRequestOwnerType.USER, person))
                .search(InformationRequestAuditSearch(limit = 500))
        }

        assertEquals(ownTargets, page.events.map { it.record.targetId }.toSet())
    }

    @Test
    fun `reconciliation matches every audited transition by business transaction and reports gaps and strays without payload values`()
    {
        val fixture = startedFixture()
        val audit = auditService(fixture)

        val clean = QuarkusTransaction.requiringNew().call { audit.reconciliation(fixture.requestId, owner(fixture)) }
        assertTrue(clean.reconciled)
        assertTrue(clean.auditedTransitionCount >= 1)

        val orphanTransition = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request_transition
                    (id, information_request_id, sequence_number, from_state, to_state, mutation, actor_kind, actor_id, occurred_at, idempotency_key)
                VALUES (?, ?, 100, 'ISSUED', 'ISSUED', 'EXTEND_CLOCK', 'USER', ?, now(), 'orphan-clock-extension')
                """.trimIndent(),
                orphanTransition,
                fixture.requestId,
                fixture.template.userId,
            )
        }
        QuarkusTransaction.requiringNew().run {
            auditRecorder.record(
                AuditEventDraft(
                    owner = AuditOwnerScope.Organization(fixture.template.organizationId),
                    eventTypeKey = AuditEventType.INFORMATION_REQUEST_CLOCK_PAUSE.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = fixture.template.userId,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = "INFORMATION_REQUEST",
                    targetId = fixture.requestId.toString(),
                    payload = mapOf("clockKey" to "response"),
                    businessTransactionId = UUID.randomUUID().toString(),
                ),
            )
        }

        val gaps = QuarkusTransaction.requiringNew().call { audit.reconciliation(fixture.requestId, owner(fixture)) }

        assertFalse(gaps.reconciled)
        assertEquals(listOf(orphanTransition), gaps.missing.map { it.transitionId })
        assertEquals("information_request.clock.extend", gaps.missing.single().expectedEventTypeKey)
        assertEquals(listOf(AuditEventType.INFORMATION_REQUEST_CLOCK_PAUSE.key), gaps.unmatched.map { it.eventTypeKey })
    }

    @Test
    fun `a record export freezes the request with its SHA-256, replays by key, verifies on read, and refuses a transfer the policy forbids`()
    {
        val fixture = startedFixture()
        val exports = exportService(fixture, transfers)

        val created = QuarkusTransaction.requiringNew().call { exports.create(command(fixture, "export-1")) }
        val replayed = QuarkusTransaction.requiringNew().call { exports.create(command(fixture, "export-1")) }
        val read = QuarkusTransaction.requiringNew()
            .call { exports.read(fixture.requestId, created.export.id, owner(fixture)) }

        assertEquals(created.export.id, replayed.export.id)
        assertTrue(read.verified)
        assertEquals(64, created.export.contentHash.length)
        assertEquals("primary", created.export.storageLocation)
        val content = Json.parseToJsonElement(requireNotNull(read.content)).jsonObject
        assertEquals(
            fixture.requestId.toString(),
            content.getValue("request").jsonObject.getValue("id").jsonPrimitive.content
        )
        assertEquals(2, content.getValue("parties").jsonArray.size)
        assertTrue(content.getValue("clocks").jsonArray.isNotEmpty())
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'information_request.request.export' AND target_id = ?",
                    fixture.requestId.toString()
                )
            )
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'information_request.request.export_read' AND target_id = ?",
                    fixture.requestId.toString()
                )
            )
            execute(
                connection,
                "ALTER TABLE information_request_record_export DISABLE TRIGGER information_request_record_export_append_only"
            )
            execute(
                connection,
                "UPDATE information_request_record_export SET content_json = '{}' WHERE id = ?",
                created.export.id
            )
            execute(
                connection,
                "ALTER TABLE information_request_record_export ENABLE TRIGGER information_request_record_export_append_only"
            )
        }
        val tampered = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew()
                .exceptionHandler { TransactionExceptionResult.COMMIT }
                .call { exports.read(fixture.requestId, created.export.id, owner(fixture)) }
        }
        assertEquals(InformationRequestErrorCatalog.EXPORT_INTEGRITY_FAILED, tampered.reasonCode)
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM audit_outbox WHERE event_type_key = 'information_request.request.export_read' AND outcome = 'FAILURE' AND target_id = ?",
                    fixture.requestId.toString()
                ),
            )
        }

        val refusing = mock<RecordTransferPolicy>()
        whenever(refusing.decide(any(), any())).thenReturn(RecordTransferVerdict.REFUSED)
        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call {
                exportService(fixture, refusing).create(
                    CreateInformationRequestRecordExportCommand(
                        fixture.requestId,
                        owner(fixture),
                        "elsewhere",
                        "export-2"
                    )
                )
            }
        }
        assertEquals(InformationRequestErrorCatalog.TRANSFER_NOT_PERMITTED, refused.reasonCode)
    }

    private fun startedFixture(): SubmissionRuntimeSqlFixture
    {
        lateinit var versionId: UUID
        val fixture = dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection)
            versionId = ClockSqlFixture(connection, runtimeFixture).versionId
            runtimeFixture
        }
        val service = InformationRequestClockService(
            runtime.build(fixture.requestId).gate, clockRepository, clockEventRepository, clockPolicies, clockRecorder,
            runtime.commandReceiptService, Clock.fixed(Instant.parse("2026-09-25T08:00:00Z"), ZoneOffset.UTC),
        )
        QuarkusTransaction.requiringNew().run {
            service.start(
                StartInformationRequestClockCommand(
                    fixture.requestId, "response", versionId, InformationRequestClockUrgency.STANDARD,
                    Instant.parse("2026-09-25T08:00:00Z"), owner(fixture), "start-response",
                ),
            )
        }
        return fixture
    }

    private fun auditService(
        fixture: SubmissionRuntimeSqlFixture,
        owner: InformationRequestOwnerRef =
            InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, fixture.template.organizationId),
    ): InformationRequestAuditService
    {
        val access = mock<InformationRequestOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(owner, Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE)).thenReturn(
            RequestAccessContext(
                PrincipalRef.user(fixture.template.userId),
                AuthorizationContext(sessionRef = "owner-session")
            )
        )
        return InformationRequestAuditService(
            runtime.build(fixture.requestId).gate,
            access,
            history,
            transitionRepository
        )
    }

    private fun exportService(
        fixture: SubmissionRuntimeSqlFixture,
        transferPolicy: RecordTransferPolicy,
        central: AuthorizationService? = null,
    ) = InformationRequestRecordExportService(
        runtime.build(fixture.requestId, centralAuthorization = central).gate,
        assembler,
        exportRepository,
        requirementRepository,
        storageLocations,
        transferPolicy,
        recordDisposals,
        runtime.commandReceiptService,
        auditRecorder,
        actorLabels,
        readAudit,
        Clock.systemUTC(),
    )

    private fun confidentialFixture(): SubmissionRuntimeSqlFixture =
        dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection, beforePublish = { configured ->
                execute(
                    connection,
                    "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = 'restricted-records' WHERE id = ?",
                    configured.documentBindingId,
                )
            })
            val packageId = UUID.randomUUID()
            runtimeFixture.insertPackage(packageId, 1)
            runtimeFixture.insertItem(
                UUID.randomUUID(), packageId, runtimeFixture.documentRequirementId, runtimeFixture.documentRevisionId,
                runtimeFixture.documentBindingId, "DOCUMENT",
            )
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, 'DECISION_MAKER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                runtimeFixture.requestId,
                runtimeFixture.template.userId,
            )
            runtimeFixture
        }

    private fun recordOwnerEvent(
        owner: AuditOwnerScope,
        eventType: AuditEventType,
        targetType: String,
        targetId: UUID,
        actorId: UUID
    ) {
        QuarkusTransaction.requiringNew().run {
            auditRecorder.record(
                AuditEventDraft(
                    owner = owner,
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = targetType,
                    targetId = targetId.toString(),
                    payload = mapOf("policyBasisKey" to "policy.basis", "requestKind" to "ACCESS"),
                ),
            )
        }
    }

    private fun documentItem(record: JsonObject) =
        record.getValue("packages").jsonArray.single().jsonObject.getValue("items").jsonArray.single().jsonObject

    private fun command(fixture: SubmissionRuntimeSqlFixture, key: String) =
        CreateInformationRequestRecordExportCommand(fixture.requestId, owner(fixture), null, key)

    private fun owner(fixture: SubmissionRuntimeSqlFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.template.userId), AuthorizationContext(sessionRef = "owner"))
}
