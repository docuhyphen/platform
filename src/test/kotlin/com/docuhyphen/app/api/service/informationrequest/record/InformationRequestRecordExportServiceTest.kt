package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestExportWindow
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestReadAudit
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import com.docuhyphen.app.api.service.recordpreservation.RecordStorageLocationPolicy
import com.docuhyphen.app.api.service.recordpreservation.RecordTransferPolicy
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

class InformationRequestRecordExportServiceTest
{
    private val organizationId = UUID.randomUUID()
    private val now = Instant.parse("2026-09-30T12:00:00Z")
    private val request = InformationRequest().apply {
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = organizationId
        state = InformationRequestState.CLOSED
    }
    private val exchange = Exchange().apply { ownerOrganizationId = organizationId }
    private val gate = mock<InformationRequestMutationGate>()
    private val assembler = mock<InformationRequestRecordAssembler>()
    private val exportRepository = mock<InformationRequestRecordExportRepository>()
    private val requirementRepository = mock<InformationRequestRequirementRepository>()
    private val storageLocations = mock<RecordStorageLocationPolicy>()
    private val transfers = mock<RecordTransferPolicy>()
    private val auditRecorder = mock<AuditRecorder>()
    private val readAudit = mock<InformationRequestReadAudit>()
    private val displays = mock<PrincipalDisplayService>()
    private val saved = mutableListOf<InformationRequestRecordExport>()
    private val service = InformationRequestRecordExportService(
        gate,
        assembler,
        exportRepository,
        requirementRepository,
        storageLocations,
        transfers,
        mock(),
        CommandReceiptService(InMemoryExportCommandReceiptStore(), CommandReceiptScope()),
        auditRecorder,
        InformationRequestAuditActorLabelResolver(displays),
        readAudit,
        Clock.fixed(now, ZoneOffset.UTC),
        InformationRequestAbuseLimits(exportDailyCeiling = 2),
    )

    init
    {
        whenever(gate.lock(request.id)).thenReturn(LockedInformationRequest(exchange, request))
        whenever(assembler.assemble(eq(request), any())).thenReturn(buildJsonObject {
            put(
                "request",
                request.id.toString()
            )
        })
        whenever(storageLocations.locationFor(any(), any())).thenReturn("primary")
        whenever(exportRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestRecordExport>(0).also { saved += it }
        }
        whenever(exportRepository.findForRequest(request.id)).thenAnswer { saved.toList() }
        whenever(exportRepository.sourcesOf(any())).thenReturn(listOf(request.id))
        whenever(exportRepository.windowSince(any(), any(), any())).thenReturn(InformationRequestExportWindow(0, null))
    }

    @Test
    fun `an owner's record exports are refused until the day's oldest one ages out once the ceiling is reached`()
    {
        whenever(
            exportRepository.windowSince(
                eq(RecordOwnerKind.ORGANIZATION),
                eq(organizationId),
                eq(Timestamp.from(now.minus(Duration.ofDays(1)))),
            ),
        ).thenReturn(InformationRequestExportWindow(2, now.minus(Duration.ofHours(20))))

        val refusal = assertThrows<InformationRequestRateLimitedException> { service.create(command("export-3")) }

        assertEquals(InformationRequestErrorCatalog.EXPORT_LIMIT_REACHED, refusal.reasonCode)
        assertEquals(Duration.ofHours(4).seconds, refusal.retryAfterSeconds)
        verify(exportRepository, never()).save(any())
    }

    @Test
    fun `an owner's export allowance is held before its day's exports are counted, and a repeated export takes neither`() {
        service.create(command("export-allowance"))
        service.create(command("export-allowance"))

        val order = inOrder(exportRepository)
        order.verify(exportRepository).lockOwnerAllowance(RecordOwnerKind.ORGANIZATION, organizationId)
        order.verify(exportRepository).windowSince(eq(RecordOwnerKind.ORGANIZATION), eq(organizationId), any())
        order.verify(exportRepository).save(any())
        verify(exportRepository, times(1)).lockOwnerAllowance(any(), any())
        verify(exportRepository, times(1)).windowSince(any(), any(), any())
    }

    @Test
    fun `a subject export takes the owner's export allowance and is refused once the day's exports reach the ceiling`() {
        whenever(gate.lockExisting(listOf(request.id))).thenReturn(listOf(LockedInformationRequest(exchange, request)))
        whenever(
            exportRepository.windowSince(
                eq(RecordOwnerKind.ORGANIZATION),
                eq(organizationId),
                eq(Timestamp.from(now.minus(Duration.ofDays(1)))),
            ),
        ).thenReturn(InformationRequestExportWindow(2, now.minus(Duration.ofHours(20))))

        val refusal = assertThrows<InformationRequestRateLimitedException> {
            service.createSubjectExport(
                com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestSubjectExportCommand(
                    owner = com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef(
                        RecordOwnerKind.ORGANIZATION,
                        organizationId
                    ),
                    subjectIdentityRefId = UUID.randomUUID(),
                    requestIds = listOf(request.id),
                    access = access(verified = true),
                    transferRegion = null,
                ),
            )
        }

        assertEquals(InformationRequestErrorCatalog.EXPORT_LIMIT_REACHED, refusal.reasonCode)
        verify(exportRepository).lockOwnerAllowance(RecordOwnerKind.ORGANIZATION, organizationId)
        verify(exportRepository, never()).save(any())
    }

    @Test
    fun `an export within the ceiling is made`()
    {
        whenever(exportRepository.windowSince(any(), any(), any()))
            .thenReturn(InformationRequestExportWindow(1, now.minus(Duration.ofHours(3))))

        service.create(command("export-2"))

        verify(exportRepository, times(1)).save(any())
    }

    @Test
    fun `a request export is audited under the requesting manager's account label and session`() {
        val session = UUID.randomUUID().toString()
        whenever(displays.userLabel(manager)).thenReturn("Record Manager <manager@process.test>")

        service.create(
            command(
                "attributed-export",
                RequestAccessContext(
                    PrincipalRef.user(manager),
                    AuthorizationContext(activeOrgId = organizationId, sessionRef = session)
                ),
            ),
        )

        val draft = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(draft.capture())
        assertEquals("information_request.request.export", draft.firstValue.eventTypeKey)
        assertEquals(manager, draft.firstValue.actorId)
        assertEquals(AuditActorKind.HUMAN, draft.firstValue.actorKind)
        assertEquals("Record Manager <manager@process.test>", draft.firstValue.actorLabel)
        assertEquals(session, draft.firstValue.sessionId)
    }

    @Test
    fun `each Requirement in a record export is decided by the caller's Requirement export permission`() {
        val requirementId = UUID.randomUUID()
        val access = access(verified = false)
        whenever(assembler.assemble(eq(request), any())).thenAnswer { invocation ->
            val discloses = invocation.getArgument<(UUID) -> Boolean>(1)
            buildJsonObject { put("disclosed", discloses(requirementId)) }
        }
        whenever(
            gate.permitsRequirement(
                access,
                Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT,
                requirementId
            )
        ).thenReturn(false)

        service.create(command("export-requirements", access))

        verify(gate).permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT, requirementId)
        assertEquals("{\"disclosed\":false}", saved.single().contentJson)
    }

    @Test
    fun `reading or replaying an export that discloses a confidential Requirement needs recent verification`() {
        val confidential = UUID.randomUUID()
        whenever(assembler.assemble(eq(request), any())).thenReturn(recordWithItem(confidential))
        whenever(requirementRepository.findCompartmentedIds(listOf(request.id))).thenReturn(setOf(confidential))
        val created = service.create(command("export-confidential", access(verified = true)))

        val listed = service.exports(request.id, access(verified = false)).single()
        val readRefusal = assertThrows<StepUpRequiredException> {
            service.read(
                request.id,
                created.export.id,
                access(verified = false)
            )
        }
        val replayRefusal = assertThrows<StepUpRequiredException> {
            service.create(
                command(
                    "export-confidential",
                    access(verified = false)
                )
            )
        }
        val read = service.read(request.id, created.export.id, access(verified = true))

        assertTrue(listed.containsConfidentialContent)
        assertEquals(401, readRefusal.response.status)
        assertEquals(401, replayRefusal.response.status)
        assertTrue(read.containsConfidentialContent)
        assertEquals(created.export.contentJson, read.content)
    }

    @Test
    fun `an export that withheld its confidential Requirements reads without recent verification`() {
        val confidential = UUID.randomUUID()
        whenever(assembler.assemble(eq(request), any())).thenReturn(recordWithItem(confidential, redacted = true))
        whenever(requirementRepository.findCompartmentedIds(listOf(request.id))).thenReturn(setOf(confidential))
        val created = service.create(command("export-withheld", access(verified = false)))

        val read = service.read(request.id, created.export.id, access(verified = false))

        assertFalse(read.containsConfidentialContent)
        assertTrue(read.verified)
        assertEquals(sha256(read.content.orEmpty()), created.export.contentHash)
    }

    @Test
    fun `a frozen export leaves out a replacement its creator may not view, and its hash covers what was stored`() {
        val replacement = UUID.randomUUID()
        val access = access(verified = false)
        whenever(assembler.assemble(eq(request), any())).thenReturn(supersededRecord(replacement))

        service.create(command("export-hidden-replacement", access))

        val frozen = saved.single()
        val stored = Json.parseToJsonElement(frozen.contentJson).jsonObject.getValue("request").jsonObject
        assertEquals(JsonNull, stored.getValue("supersededByRequestId"))
        assertTrue(stored.getValue("supersededByRequestRedacted").jsonPrimitive.boolean)
        assertFalse(frozen.contentJson.contains(replacement.toString()))
        assertEquals(sha256(frozen.contentJson), frozen.contentHash)
        verify(gate).permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, replacement)
    }

    @Test
    fun `a creator who may view the replacement freezes the export exactly as assembled`() {
        val replacement = UUID.randomUUID()
        val access = access(verified = false)
        whenever(assembler.assemble(eq(request), any())).thenReturn(supersededRecord(replacement))
        whenever(gate.permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, replacement)).thenReturn(true)

        service.create(command("export-visible-replacement", access))

        assertEquals(supersededRecord(replacement).toString(), saved.single().contentJson)
    }

    @Test
    fun `reading a retained export leaves out a replacement its reader may no longer view and keeps the stored record`() {
        val replacement = UUID.randomUUID()
        val access = access(verified = false)
        whenever(assembler.assemble(eq(request), any())).thenReturn(supersededRecord(replacement))
        whenever(gate.permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, replacement)).thenReturn(true)
        val created = service.create(command("export-retained", access))
        val stored = created.export.contentJson
        whenever(gate.permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, replacement)).thenReturn(false)

        val read = service.read(request.id, created.export.id, access)

        assertTrue(read.verified)
        assertEquals(1, read.withheldRelatedRequestCount)
        assertFalse(read.content.orEmpty().contains(replacement.toString()))
        assertTrue(
            Json.parseToJsonElement(read.content.orEmpty()).jsonObject.getValue("request").jsonObject
                .getValue("supersededByRequestRedacted").jsonPrimitive.boolean,
        )
        assertEquals(stored, saved.single().contentJson)
        assertTrue(stored.contains(replacement.toString()))
        assertEquals(sha256(stored), read.export.contentHash)
    }

    @Test
    fun `a reader who may view the replacement reads the stored export unchanged`() {
        val replacement = UUID.randomUUID()
        val access = access(verified = false)
        whenever(assembler.assemble(eq(request), any())).thenReturn(supersededRecord(replacement))
        whenever(gate.permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, replacement)).thenReturn(true)
        val created = service.create(command("export-shown", access))

        val read = service.read(request.id, created.export.id, access)

        assertEquals(created.export.contentJson, read.content)
        assertEquals(0, read.withheldRelatedRequestCount)
    }

    @Test
    fun `a subject export keeps its related requests for its owner and leaves out, through a request, one its reader may not view`() {
        val replacement = UUID.randomUUID()
        val owner =
            com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef(RecordOwnerKind.ORGANIZATION, organizationId)
        val access = access(verified = true)
        whenever(gate.lockExisting(listOf(request.id))).thenReturn(listOf(LockedInformationRequest(exchange, request)))
        whenever(assembler.assemble(eq(request), any())).thenReturn(supersededRecord(replacement))
        whenever(gate.permitsRequest(access, Action.INFORMATION_REQUEST_EXPORT, request.id)).thenReturn(true)
        whenever(exportRepository.findById(any())).thenAnswer { invocation ->
            saved.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
        }
        val created = service.createSubjectExport(
            com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestSubjectExportCommand(
                owner = owner,
                subjectIdentityRefId = UUID.randomUUID(),
                requestIds = listOf(request.id),
                access = access,
                transferRegion = null,
            ),
        )

        val forOwner = service.readSubjectExport(created.export.id, owner, access)
        val throughRequest = service.read(request.id, created.export.id, access)

        assertEquals(created.export.contentJson, forOwner.content)
        assertTrue(created.export.contentJson.contains(replacement.toString()))
        assertEquals(0, forOwner.withheldRelatedRequestCount)
        assertFalse(throughRequest.content.orEmpty().contains(replacement.toString()))
        assertEquals(1, throughRequest.withheldRelatedRequestCount)
        assertTrue(throughRequest.verified)
    }

    @Test
    fun `an oversized record export is refused before storing its content`() {
        whenever(assembler.assemble(eq(request), any())).thenReturn(buildJsonObject {
            put(
                "narrative",
                "a".repeat(25 * 1024 * 1024)
            )
        })
        val refusal =
            assertThrows<com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException> {
                service.create(command("oversized-record-export"))
            }
        assertEquals("INFORMATION_REQUEST_RECORD_EXPORT_TOO_LARGE", refusal.reasonCode)
        verify(exportRepository, never()).save(any())
    }

    private fun supersededRecord(replacement: UUID): JsonObject = buildJsonObject {
        put("schemaVersion", 4)
        putJsonObject("request") {
            put("id", request.id.toString())
            put("state", "SUPERSEDED")
            put("supersededByRequestId", replacement.toString())
        }
        putJsonArray("recertifications") { }
    }

    private fun recordWithItem(requirementId: UUID, redacted: Boolean = false): JsonObject = buildJsonObject {
        put("schemaVersion", 4)
        putJsonArray("packages") {
            addJsonObject {
                putJsonArray("items") {
                    addJsonObject {
                        put("itemId", UUID.randomUUID().toString())
                        put("requirementId", requirementId.toString())
                        if (redacted) put("redacted", true) else put("narrative", "Recorded narrative")
                    }
                }
            }
        }
        putJsonArray("itemCorrections") { add(buildJsonObject { }) }
    }

    private fun access(verified: Boolean) =
        RequestAccessContext(
            PrincipalRef.user(manager),
            AuthorizationContext(activeOrgId = organizationId, mfaSatisfied = verified)
        )

    private fun command(idempotencyKey: String, access: RequestAccessContext = access(verified = false)) =
        CreateInformationRequestRecordExportCommand(
            requestId = request.id,
            access = access,
            transferRegion = null,
            idempotencyKey = idempotencyKey,
        )

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val manager: UUID = UUID.randomUUID()
    }
}

private class InMemoryExportCommandReceiptStore : CommandReceiptStore
{
    private val receipts = mutableListOf<CommandReceipt>()

    override fun lockCommand(request: CommandReceiptRequest) = Unit

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                    it.resourceId == request.resource.id &&
                    it.operationName == request.operation &&
                    it.actorKind == request.actor.kind &&
                    it.actorId == request.actor.id &&
                    it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}
