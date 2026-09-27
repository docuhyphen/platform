package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestNoticeDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoticeQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/notices")
@Produces(APPLICATION_JSON)
class InformationRequestNoticeResource @Inject constructor(
    private val notices: InformationRequestNoticeQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            val views = notices.notices(InformationRequestCommandHttp.uuid(id, "information request id"), accessContextFactory.currentAuthenticated())
            Response.ok(views.map { InformationRequestNoticeDtoMapper.toDto(it, notices.allocationsOf(it)) }.toTypedArray()).build()
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
