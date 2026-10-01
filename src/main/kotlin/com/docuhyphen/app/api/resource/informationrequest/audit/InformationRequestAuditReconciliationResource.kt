package com.docuhyphen.app.api.resource.informationrequest.audit

import com.docuhyphen.app.api.model.InformationRequestAuditDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.audit.operations.InformationRequestAuditReconciliationResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestAuditReconciliationResource @Inject constructor(
    private val audit: InformationRequestAuditService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestAuditReconciliationResourceOperations
{
    override fun get(id: String): Response
    {
        return try
        {
            val requestId = InformationRequestCommandHttp.uuid(id, "information request id")
            Response.ok(
                InformationRequestAuditDtoMapper.toDto(
                    audit.reconciliation(
                        requestId,
                        accessContextFactory.currentAuthenticated()
                    )
                )
            ).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request audit reconciliation failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAuditReconciliationResource::class.java)
    }
}
