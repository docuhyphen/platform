package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestPrivacyDtoMapper
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemCorrectionInput
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.resource.model.LiftInformationRequestSubjectRestrictionRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestPrivacyRequestRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestPrivacyService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestSubjectRestrictionService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-request-subject-restrictions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestSubjectRestrictionResource @Inject constructor(
    private val restrictions: InformationRequestSubjectRestrictionService,
)
{
    @GET
    fun list(): Response
    {
        return try
        {
            Response.ok(restrictions.restrictions().map(InformationRequestPrivacyDtoMapper::toDto).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request subject restriction list failed", exception)
        }
    }

    @POST
    @Path("/{restrictionId}/lift")
    fun lift(@PathParam("restrictionId") restrictionId: String, request: LiftInformationRequestSubjectRestrictionRequest?): Response
    {
        return try
        {
            val body = request ?: throw InformationRequestCommandRequestException("Lifting a restriction states its reason")
            val lifted = restrictions.lift(InformationRequestCommandHttp.uuid(restrictionId, "restriction id"), body.reasonCode)
            Response.ok(InformationRequestPrivacyDtoMapper.toDto(lifted)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request subject restriction lift failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSubjectRestrictionResource::class.java)
    }
}
