package com.docuhyphen.app.api.resource.informationrequest.record

import com.docuhyphen.app.api.model.dto.InformationRequestRecordExportDto
import com.docuhyphen.app.api.model.entity.InformationRequestRecordExport
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestRecordExportView
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

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
        whenever(contexts.currentAuthenticated()).thenReturn(RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext()))
    }

    @Test
    fun `request records are sub-resources of a request and owner-scope records are top-level collections`()
    {
        assertEquals("/information-requests/{id}/record-exports", pathOf(InformationRequestRecordExportResourceOperations::class.java))
        assertEquals("/information-requests/{id}/audit-events", pathOf(InformationRequestAuditEventResourceOperations::class.java))
        assertEquals("/information-requests/{id}/audit-reconciliation", pathOf(InformationRequestAuditReconciliationResourceOperations::class.java))
        assertEquals("/information-requests/{id}/disposal-standing", pathOf(InformationRequestDisposalStandingResourceOperations::class.java))
        assertEquals("/information-request-audit-events", pathOf(InformationRequestAuditSearchResourceOperations::class.java))
        assertEquals("/information-request-privacy-requests", pathOf(InformationRequestPrivacyRequestResourceOperations::class.java))
        assertEquals("/information-request-subject-restrictions", pathOf(InformationRequestSubjectRestrictionResourceOperations::class.java))
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
        val backwards = auditResource.list(requestId, null, null, null, "2026-09-26T12:00:00Z", "2026-09-26T08:00:00Z", null, null)
        assertEquals(400, backwards.status)
        assertEquals("occurredBefore must be later than occurredAfter", (backwards.entity as ResponseError).errorMessage)
        assertEquals(400, InformationRequestDisposalStandingResource(disposals, contexts).get("not-a-request").status)
        assertEquals(400, InformationRequestPrivacyRequestResource(privacy).record(null).status)
        assertEquals(400, InformationRequestSubjectRestrictionResource(restrictions).lift(UUID.randomUUID().toString(), null).status)
        verifyNoInteractions(exports, audit, disposals, privacy, restrictions)
    }

    @Test
    fun `a record the caller cannot see is not found and carries its stable reason`()
    {
        whenever(exports.exports(any(), any()))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Information Request not found"))

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
        whenever(exports.read(any(), any(), any())).thenReturn(InformationRequestRecordExportView(export, listOf(requestId), true, export.contentJson))

        val response = InformationRequestRecordExportResource(exports, contexts).get(requestId.toString(), export.id.toString())

        assertEquals(200, response.status)
        val body = response.entity as InformationRequestRecordExportDto
        assertEquals(true, body.verified)
        assertEquals("a".repeat(64), body.contentHash)
        assertEquals(listOf(requestId), body.sourceRequestIds)
        assertNull(response.getHeaderString("Digest"))
    }

    private fun pathOf(type: Class<*>): String = type.getAnnotation(Path::class.java).value
}
