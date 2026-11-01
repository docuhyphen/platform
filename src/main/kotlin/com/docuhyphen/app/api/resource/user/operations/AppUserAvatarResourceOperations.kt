package com.docuhyphen.app.api.resource.user.operations

import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import java.io.File
import java.util.*

@Path("/app-user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AppUserAvatarResourceOperations {
    @POST
    @Path("/avatar")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadAvatar(
        @RestForm("file") file: File?,
        @RestForm("extension") extension: String?,
    ): Response

    @GET
    @Path("/avatar")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getAvatar(): Response

    @GET
    @Path("/{id}/avatar")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getUserAvatar(@PathParam("id") id: UUID): Response

    @DELETE
    @Path("/avatar")
    fun deleteAvatar(): Response
}
