package com.docuhyphen.app.api.resource.informationrequest.notice

import com.docuhyphen.app.api.model.InformationRequestNoticeDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.notice.operations.InformationRequestNoticeResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoticeResource @Inject constructor(
    private val notices: InformationRequestNoticeQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestNoticeResourceOperations
{
    override fun list(id: String): Response
    {
        return try
        {
            val views = notices.notices(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                accessContextFactory.currentAuthenticated()
            )
            Response.ok(views.map { InformationRequestNoticeDtoMapper.toDto(it, notices.allocationsOf(it)) }
                .toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request notice history failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoticeResource::class.java)
    }
}
