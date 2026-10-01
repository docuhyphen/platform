package com.docuhyphen.app.api.resource.informationrequest.privacy.operations

import com.docuhyphen.app.api.resource.model.LiftInformationRequestSubjectRestrictionRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-subject-restrictions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestSubjectRestrictionResourceOperations
{
    @GET
    fun list(): Response

    @POST
    @Path("/{restrictionId}/lift")
    fun lift(
        @PathParam("restrictionId") restrictionId: String,
        request: LiftInformationRequestSubjectRestrictionRequest?
    ): Response
}
