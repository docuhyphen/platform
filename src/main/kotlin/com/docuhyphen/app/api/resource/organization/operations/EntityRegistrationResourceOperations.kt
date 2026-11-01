package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.model.OrganizationRegistrationRequest
import com.docuhyphen.app.api.resource.model.PersonRegistrationRequest
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/entity-registration")
interface EntityRegistrationResourceOperations {
    @POST
    @Path("/person")
    @Produces(APPLICATION_JSON)
    fun registerPerson(personRegistrationRequest: PersonRegistrationRequest?): Response

    @POST
    @Path("/organization")
    @Produces(APPLICATION_JSON)
    fun registerOrganization(organizationRegistrationRequest: OrganizationRegistrationRequest?): Response
}
