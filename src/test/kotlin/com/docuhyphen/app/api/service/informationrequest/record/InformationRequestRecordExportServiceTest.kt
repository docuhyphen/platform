package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestExportWindow
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import com.docuhyphen.app.api.service.recordpreservation.RecordStorageLocationPolicy
import com.docuhyphen.app.api.service.recordpreservation.RecordTransferPolicy
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

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
    private val storageLocations = mock<RecordStorageLocationPolicy>()
    private val transfers = mock<RecordTransferPolicy>()
    private val auditRecorder = mock<AuditRecorder>()
    private val requestRepository = mock<InformationRequestRepository>()
    private val service = InformationRequestRecordExportService(
        gate,
        assembler,
        exportRepository,
        storageLocations,
        transfers,
        CommandReceiptService(InMemoryExportCommandReceiptStore()),
        auditRecorder,
        requestRepository,
        Clock.fixed(now, ZoneOffset.UTC),
        InformationRequestAbuseLimits(exportDailyCeiling = 2),
    )

    init
    {
        whenever(gate.lock(request.id)).thenReturn(LockedInformationRequest(exchange, request))
        whenever(assembler.assemble(request)).thenReturn(buildJsonObject { put("request", request.id.toString()) })
        whenever(storageLocations.locationFor(any(), any())).thenReturn("primary")
        whenever(exportRepository.save(any())).thenAnswer { it.getArgument(0) }
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
    fun `an export within the ceiling is made`()
    {
        whenever(exportRepository.windowSince(any(), any(), any()))
            .thenReturn(InformationRequestExportWindow(1, now.minus(Duration.ofHours(3))))

        service.create(command("export-2"))

        verify(exportRepository, times(1)).save(any())
    }

    private fun command(idempotencyKey: String) = CreateInformationRequestRecordExportCommand(
        requestId = request.id,
        access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(activeOrgId = organizationId)),
        transferRegion = null,
        idempotencyKey = idempotencyKey,
    )
}

private class InMemoryExportCommandReceiptStore : CommandReceiptStore
{
    private val receipts = mutableListOf<CommandReceipt>()

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
