package com.docuhyphen.app.api.resource.informationrequest.audit

import com.docuhyphen.app.api.model.InformationRequestAuditDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.audit.operations.InformationRequestAuditSearchResourceOperations
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestAuditSearchResource @Inject constructor(
    private val audit: InformationRequestAuditService,
) : InformationRequestAuditSearchResourceOperations
{
    @Suppress("LongParameterList")
    override fun search(
        requestId: String?,
        eventClass: String?,
        eventType: String?,
        actorId: String?,
        occurredAfter: String?,
        occurredBefore: String?,
        limit: Int?,
        offset: Int?,
    ): Response
    {
        return try
        {
            val search = InformationRequestAuditQuery.searchOf(
                requestId?.takeIf { it.isNotBlank() }?.let { InformationRequestCommandHttp.uuid(it, "information request id") },
                eventClass, eventType, actorId, occurredAfter, occurredBefore, limit, offset,
            )
            Response.ok(InformationRequestAuditDtoMapper.toDto(audit.search(search))).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request audit search failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAuditSearchResource::class.java)
    }
}
