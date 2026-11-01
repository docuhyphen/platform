package com.docuhyphen.app.api.resource.user.operations

import com.docuhyphen.app.api.model.dto.AppUserSettingsDto
import com.docuhyphen.app.api.model.dto.PersonBasicDto
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/app-user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AppUserResourceOperations {
    @GET
    fun getSignInAppUser(): Response

    @GET
    @Path("/session")
    fun getCurrentSession(): Response

    @GET
    @Path("{appUserId}/person/{personId}/organization")
    fun getAppUserPersonOrganization(
        @PathParam("appUserId") appUserId: String,
        @PathParam("personId") personId: String,
    ): Response

    @PUT
    @Path("/settings")
    fun updateCurrentUserSettings(settingsDto: AppUserSettingsDto): Response

    @PUT
    @Path("/{userId}/settings")
    fun updateUserSettings(
        @PathParam("userId") userId: String,
        settingsDto: AppUserSettingsDto,
    ): Response

    @PUT
    @Path("/person")
    fun updatePerson(personDto: PersonBasicDto): Response
}
