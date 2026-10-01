package com.docuhyphen.app.api.resource.informationrequest.audit

import com.docuhyphen.app.api.model.InformationRequestAuditDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.audit.operations.InformationRequestAuditEventResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestAuditEventResource @Inject constructor(
    private val audit: InformationRequestAuditService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestAuditEventResourceOperations
{
    @Suppress("LongParameterList")
    override fun list(
        id: String,
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
            val requestId = InformationRequestCommandHttp.uuid(id, "information request id")
            val search = InformationRequestAuditQuery.searchOf(
                null,
                eventClass,
                eventType,
                actorId,
                occurredAfter,
                occurredBefore,
                limit,
                offset
            )
            Response.ok(
                InformationRequestAuditDtoMapper.toDto(
                    audit.events(
                        requestId,
                        accessContextFactory.currentAuthenticated(),
                        search
                    )
                )
            ).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request audit history failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAuditEventResource::class.java)
    }
}
