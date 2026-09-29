package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubjectService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-request-subjects")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestSubjectResource @Inject constructor(
    private val subjectService: InformationRequestSubjectService,
)
{
    @GET
    fun list(): Response
    {
        return try
        {
            Response.ok(subjectService.listForOwner()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request subject list failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSubjectResource::class.java)
    }
}
