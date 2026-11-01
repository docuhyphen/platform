package com.docuhyphen.app.api.resource.organization.operations

import com.docuhyphen.app.api.resource.model.AddPersonalGroupMembersRequest
import com.docuhyphen.app.api.resource.model.CreatePersonalGroupRequest
import com.docuhyphen.app.api.resource.model.UpdatePersonalGroupRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestForm
import java.io.File

@Path("me/groups")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface PersonalGroupResourceOperations {
    @POST
    fun createGroup(request: CreatePersonalGroupRequest): Response

    @GET
    fun listGroups(): Response

    @GET
    @Path("/{groupId}")
    fun getGroup(@PathParam("groupId") groupId: String): Response

    @PUT
    @Path("/{groupId}")
    fun updateGroup(
        @PathParam("groupId") groupId: String,
        request: UpdatePersonalGroupRequest,
    ): Response

    @POST
    @Path("/{groupId}/members")
    fun addMembers(
        @PathParam("groupId") groupId: String,
        request: AddPersonalGroupMembersRequest,
    ): Response

    @DELETE
    @Path("/{groupId}/members/{principalId}")
    fun removeMember(
        @PathParam("groupId") groupId: String,
        @PathParam("principalId") principalId: String,
        @QueryParam("principalKind") principalKindParam: String?,
    ): Response

    @DELETE
    @Path("/{groupId}")
    fun deleteGroup(@PathParam("groupId") groupId: String): Response

    @POST
    @Path("/{groupId}/icon")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun uploadGroupIcon(
        @RestForm("file") file: File?,
        @RestForm("contentType") contentType: String?,
        @PathParam("groupId") groupId: String,
    ): Response

    @DELETE
    @Path("/{groupId}/icon")
    fun deleteGroupIcon(@PathParam("groupId") groupId: String): Response
}
