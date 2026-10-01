package com.docuhyphen.app.api.resource.informationrequest.template.operations

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-request-configuration-bundles/validations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestConfigurationBundleResourceOperations
{
    @POST
    fun validate(document: String?): Response
}
