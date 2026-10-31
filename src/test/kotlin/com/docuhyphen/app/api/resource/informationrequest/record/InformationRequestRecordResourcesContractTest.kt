package com.docuhyphen.app.api.resource.informationrequest.record

import com.docuhyphen.app.api.model.InformationRequestAuditDtoMapper
import com.docuhyphen.app.api.model.audit.AuditTargetRecord
import com.docuhyphen.app.api.model.dto.InformationRequestRecordExportDto
import com.docuhyphen.app.api.model.entity.InformationRequestPrivacyRequestKind
import com.docuhyphen.app.api.model.entity.InformationRequestRecordExport
import com.docuhyphen.app.api.model.entity.InformationRequestRecordExportKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestAuditEvent
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestAuditPage
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestRecordExportView
import com.docuhyphen.app.api.model.informationrequest.privacy.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.resource.informationrequest.audit.InformationRequestAuditEventResource
import com.docuhyphen.app.api.resource.informationrequest.audit.operations.InformationRequestAuditEventResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.audit.operations.InformationRequestAuditReconciliationResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.audit.operations.InformationRequestAuditSearchResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.disposal.InformationRequestDisposalStandingResource
import com.docuhyphen.app.api.resource.informationrequest.disposal.operations.InformationRequestDisposalStandingResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.privacy.InformationRequestPrivacyRequestResource
import com.docuhyphen.app.api.resource.informationrequest.privacy.InformationRequestSubjectRestrictionResource
import com.docuhyphen.app.api.resource.informationrequest.privacy.operations.InformationRequestPrivacyRequestResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.privacy.operations.InformationRequestSubjectRestrictionResourceOperations
import com.docuhyphen.app.api.resource.informationrequest.record.operations.InformationRequestRecordExportResourceOperations
import com.docuhyphen.app.api.resource.model.LiftInformationRequestSubjectRestrictionRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestPrivacyRequestRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditService
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalQueryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestPrivacyService
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestSubjectRestrictionService
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

class InformationRequestRecordResourcesContractTest
{
    private val contexts: InformationRequestAccessContextFactory = mock()
    private val exports: InformationRequestRecordExportService = mock()
    private val audit: InformationRequestAuditService = mock()
    private val disposals: InformationRequestDisposalQueryService = mock()
    private val privacy: InformationRequestPrivacyService = mock()
    private val restrictions: InformationRequestSubjectRestrictionService = mock()

    init
    {
        whenever(contexts.currentAuthenticated()).thenReturn(
            RequestAccessContext(
                PrincipalRef.user(UUID.randomUUID()),
                AuthorizationContext()
            )
        )
    }

    @Test
    fun `request records are sub-resources of a request and owner-scope records are top-level collections`()
    {
        assertEquals(
            "/information-requests/{id}/record-exports",
            pathOf(InformationRequestRecordExportResourceOperations::class.java)
        )
        assertEquals(
            "/information-requests/{id}/audit-events",
            pathOf(InformationRequestAuditEventResourceOperations::class.java)
        )
        assertEquals(
            "/information-requests/{id}/audit-reconciliation",
            pathOf(InformationRequestAuditReconciliationResourceOperations::class.java)
        )
        assertEquals(
            "/information-requests/{id}/disposal-standing",
            pathOf(InformationRequestDisposalStandingResourceOperations::class.java)
        )
        assertEquals(
            "/information-request-audit-events",
            pathOf(InformationRequestAuditSearchResourceOperations::class.java)
        )
        assertEquals(
            "/information-request-privacy-requests",
            pathOf(InformationRequestPrivacyRequestResourceOperations::class.java)
        )
        assertEquals(
            "/information-request-subject-restrictions",
            pathOf(InformationRequestSubjectRestrictionResourceOperations::class.java)
        )
    }

    @Test
    fun `malformed input is a bad request that never reaches a service`()
    {
        val requestId = UUID.randomUUID().toString()
        val exportResource = InformationRequestRecordExportResource(exports, contexts)
        val auditResource = InformationRequestAuditEventResource(audit, contexts)

        assertEquals(400, exportResource.create(requestId, null, null).status)
        assertEquals(400, exportResource.get(requestId, "not-an-export").status)
        assertEquals(400, auditResource.list(requestId, null, null, null, null, null, 0, null).status)
        assertEquals(400, auditResource.list(requestId, "no such class", null, null, null, null, null, null).status)
        val backwards =
            auditResource.list(requestId, null, null, null, "2026-09-26T12:00:00Z", "2026-09-26T08:00:00Z", null, null)
        assertEquals(400, backwards.status)
        assertEquals(
            "occurredBefore must be later than occurredAfter",
            (backwards.entity as ResponseError).errorMessage
        )
        assertEquals(400, InformationRequestDisposalStandingResource(disposals, contexts).get("not-a-request").status)
        assertEquals(400, InformationRequestPrivacyRequestResource(privacy, contexts).record(null, "privacy-1").status)
        assertEquals(
            400,
            InformationRequestSubjectRestrictionResource(restrictions).lift(
                UUID.randomUUID().toString(),
                null,
                "lift-1"
            ).status
        )
        verifyNoInteractions(exports, audit, disposals, privacy, restrictions)
    }

    @Test
    fun `a record the caller cannot see is not found and carries its stable reason`()
    {
        whenever(exports.exports(any(), any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request not found"
                )
            )

        val response = InformationRequestRecordExportResource(exports, contexts).list(UUID.randomUUID().toString())

        assertEquals(404, response.status)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, (response.entity as ResponseError).reasonCode)
    }

    @Test
    fun `a record export read returns the verified record in its body and states its hash only there`()
    {
        val requestId = UUID.randomUUID()
        val export = InformationRequestRecordExport().apply {
            informationRequestId = requestId
            ownerId = UUID.randomUUID()
            contentJson = "{\"schemaVersion\":1}"
            contentHash = "a".repeat(64)
            contentLength = contentJson.length.toLong()
            storageLocation = "primary"
            requestedByPrincipalId = UUID.randomUUID()
        }
        whenever(exports.read(any(), any(), any())).thenReturn(
            InformationRequestRecordExportView(
                export,
                listOf(requestId),
                true,
                export.contentJson,
                true
            )
        )

        val response =
            InformationRequestRecordExportResource(exports, contexts).get(requestId.toString(), export.id.toString())

        assertEquals(200, response.status)
        val body = response.entity as InformationRequestRecordExportDto
        assertEquals(true, body.verified)
        assertEquals("a".repeat(64), body.contentHash)
        assertEquals(listOf(requestId), body.sourceRequestIds)
        assertEquals(true, body.containsConfidentialContent)
        assertNull(response.getHeaderString("Digest"))
    }

    @Test
    fun `a privacy request's subject record is read from the owner-scope privacy collection`() {
        val privacyRequestId = UUID.randomUUID()
        val sources = listOf(UUID.randomUUID(), UUID.randomUUID())
        val export = InformationRequestRecordExport().apply {
            exportKind = InformationRequestRecordExportKind.SUBJECT_RECORD
            subjectIdentityRefId = UUID.randomUUID()
            ownerId = UUID.randomUUID()
            contentJson = "{\"schemaVersion\":4}"
            contentHash = "b".repeat(64)
            contentLength = contentJson.length.toLong()
            storageLocation = "primary"
            requestedByPrincipalId = UUID.randomUUID()
        }
        whenever(privacy.subjectExport(privacyRequestId)).thenReturn(
            InformationRequestRecordExportView(
                export,
                sources,
                true,
                export.contentJson,
                false
            )
        )
        val resource = InformationRequestPrivacyRequestResource(privacy, contexts)

        val response = resource.recordExport(privacyRequestId.toString())
        val malformed = resource.recordExport("not-a-privacy-request")

        assertEquals(200, response.status)
        val body = response.entity as InformationRequestRecordExportDto
        assertEquals(InformationRequestRecordExportKind.SUBJECT_RECORD, body.exportKind)
        assertEquals(sources, body.sourceRequestIds)
        assertEquals("{\"schemaVersion\":4}", body.content.toString())
        assertEquals(400, malformed.status)
        verify(privacy).subjectExport(privacyRequestId)
    }

    @Test
    fun `a privacy request and a restriction lift need an idempotency key before they reach a service`() {
        val subjectId = UUID.randomUUID().toString()
        val body = RecordInformationRequestPrivacyRequestRequest(
            subjectId,
            InformationRequestPrivacyRequestKind.ACCESS,
            "subject.request",
            "policy.basis"
        )

        val privacyRefusal = InformationRequestPrivacyRequestResource(privacy, contexts).record(body, null)
        val blankRefusal = InformationRequestPrivacyRequestResource(privacy, contexts).record(body, "  ")
        val liftRefusal = InformationRequestSubjectRestrictionResource(restrictions)
            .lift(
                UUID.randomUUID().toString(),
                LiftInformationRequestSubjectRestrictionRequest("subject.resolved"),
                null
            )

        assertEquals(400, privacyRefusal.status)
        assertEquals(400, blankRefusal.status)
        assertEquals(400, liftRefusal.status)
        verifyNoInteractions(privacy, restrictions)
    }

    @Test
    fun `a privacy request carries its idempotency key and the caller's verification to the service`() {
        val subjectId = UUID.randomUUID()
        whenever(privacy.submit(any())).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Subject not found"
            )
        )
        val body = RecordInformationRequestPrivacyRequestRequest(
            subjectId.toString(),
            InformationRequestPrivacyRequestKind.ACCESS,
            "subject.request",
            "policy.basis"
        )

        val response = InformationRequestPrivacyRequestResource(privacy, contexts).record(body, "privacy-1")

        assertEquals(404, response.status)
        val command = argumentCaptor<RecordInformationRequestPrivacyRequestCommand>()
        verify(privacy).submit(command.capture())
        assertEquals("privacy-1", command.firstValue.idempotencyKey)
        assertEquals(subjectId, command.firstValue.subjectIdentityRefId)
        assertEquals(AuthorizationContext(), command.firstValue.authorization)
    }

    @Test
    fun `an owner audit event names its target and states a request only when it targets one`() {
        val privacyRequestId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val page = InformationRequestAuditPage(
            events = listOf(
                auditEvent("INFORMATION_REQUEST_PRIVACY_REQUEST", privacyRequestId.toString()),
                auditEvent("INFORMATION_REQUEST", requestId.toString()),
            ),
            total = 2,
            limit = 100,
            offset = 0,
        )

        val items = InformationRequestAuditDtoMapper.toDto(page).items

        assertEquals("INFORMATION_REQUEST_PRIVACY_REQUEST", items[0].targetType)
        assertEquals(privacyRequestId.toString(), items[0].targetId)
        assertNull(items[0].requestId)
        assertEquals("INFORMATION_REQUEST", items[1].targetType)
        assertEquals(requestId.toString(), items[1].requestId)
    }

    private fun auditEvent(targetType: String, targetId: String) = InformationRequestAuditEvent(
        record = AuditTargetRecord(
            eventId = UUID.randomUUID(),
            eventTypeKey = "information_request.privacy.record",
            category = "INFORMATION_REQUEST",
            outcome = "SUCCESS",
            occurredAt = Instant.parse("2026-10-01T08:00:00Z"),
            recordedAt = Instant.parse("2026-10-01T08:00:00Z"),
            actorKind = "HUMAN",
            actorId = UUID.randomUUID(),
            targetType = targetType,
            targetId = targetId,
            businessTransactionId = null,
            payload = emptyMap(),
            sealed = false,
            eventHash = null,
            streamSequence = null,
        ),
        eventClass = "privacy",
        payload = emptyMap(),
        withheldKeyCount = 0,
    )

    private fun pathOf(type: Class<*>): String = type.getAnnotation(Path::class.java).value
}
