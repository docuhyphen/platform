package com.docuhyphen.app.api.resource.exchange.operations

import com.docuhyphen.app.api.resource.model.*
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/exchanges")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ExchangeResourceOperations {
    @POST
    fun initiateExchange(exchangeInitiationDto: ExchangeInitiationDto): Response

    @HEAD
    fun checkUserHasExchanges(): Response

    @GET
    fun getExchanges(): Response

    @GET
    @Path("/{exchangeId}")
    fun getExchange(@PathParam("exchangeId") exchangeId: String): Response

    @GET
    @Path("/{exchangeId}/access")
    fun getExchangeAccess(@PathParam("exchangeId") exchangeId: String): Response

    @POST
    @Path("/{exchangeId}/access")
    fun grantExchangeAccess(
        @PathParam("exchangeId") exchangeId: String,
        request: GrantSessionShareRequest,
    ): Response

    @POST
    @Path("/{exchangeId}/recipient-invitations")
    fun inviteTrustedParticipant(
        @PathParam("exchangeId") exchangeId: String,
        request: InviteTrustedParticipantRequest,
    ): Response

    @POST
    @Path("/{exchangeId}/recipient-invitations/primary/resend")
    fun resendPrimaryRecipientInvitation(
        @PathParam("exchangeId") exchangeId: String,
    ): Response

    @PATCH
    @Path("/{exchangeId}/access/{shareId}")
    fun updateExchangeAccessRole(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("shareId") shareId: String,
        request: UpdateSessionShareRoleRequest,
    ): Response

    @DELETE
    @Path("/{exchangeId}/access/{shareId}")
    fun revokeExchangeAccess(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("shareId") shareId: String,
    ): Response

    @POST
    @Path("/{exchangeId}/access/primary-recipient")
    fun replacePrimaryRecipient(
        @PathParam("exchangeId") exchangeId: String,
        request: ReplacePrimaryRecipientRequest,
    ): Response

    @PUT
    @Path("/{exchangeId}")
    fun updateExchange(
        @PathParam("exchangeId") exchangeId: String,
        request: UpdateExchangeRequest
    ): Response

    @POST
    @Path("/{exchangeId}/rescind")
    fun rescindExchange(@PathParam("exchangeId") exchangeId: String): Response

    @POST
    @Path("/{exchangeId}/recipient-otp")
    fun issueSessionRecipientOtp(@PathParam("exchangeId") exchangeId: String): Response

    @DELETE
    @Path("/{exchangeId}")
    fun deleteExchange(@PathParam("exchangeId") exchangeId: String): Response

    @GET
    @Path("/search")
    fun searchExchanges(
        @QueryParam("query") query: String?,
        @QueryParam("status") status: String?,
        @QueryParam("initiatedBy") initiatedBy: Boolean?,
        @QueryParam("page") page: Int = 0,
        @QueryParam("size") size: Int = 20,
        @QueryParam("sortBy") sortBy: String = "createdDate",
        @QueryParam("sortDirection") sortDirection: String = "DESC"
    ): Response

    @GET
    @Path("/{exchangeId}/workflow-instances")
    fun getExchangeWorkflowInstances(@PathParam("exchangeId") exchangeId: String): Response

    @GET
    @Path("/{exchangeId}/workflow-clearance-status")
    fun getExchangeWorkflowClearanceStatus(@PathParam("exchangeId") exchangeId: String): Response
}
