package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.model.dto.ExchangeRecipientInvitationDto
import com.docuhyphen.app.api.resource.model.ExchangeRecipientInvitationDecisionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientInvitationService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/exchange-recipient-invitations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExchangeRecipientInvitationResource @Inject constructor(
    private val invitationService: ExchangeRecipientInvitationService,
)
{
    @GET
    fun listPending(): Response =
        try
        {
            val invitations = invitationService.listPending()
            Response.ok(
                object : GenericEntity<List<ExchangeRecipientInvitationDto>>(invitations) {},
            ).build()
        }
        catch (exception: ForbiddenException)
        {
            Response.status(Response.Status.FORBIDDEN)
                .entity(ResponseError(exception.message))
                .build()
        }

    @POST
    @Path("/{recipientId}/decisions")
    fun decide(
        @PathParam("recipientId") recipientId: String,
        request: ExchangeRecipientInvitationDecisionRequest,
    ): Response =
        try
        {
            invitationService.decide(
                recipientId = UUID.fromString(recipientId),
                decision = request.decision,
            )
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError(exception.message))
                        .build()
                is OrganizationTrustException ->
                    Response.status(Response.Status.CONFLICT)
                        .entity(ResponseError("This trusted participant can no longer accept the invitation"))
                        .build()
                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST)
                        .entity(ResponseError(exception.message))
                        .build()
                else ->
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while deciding the participant invitation"))
                        .build()
            }
        }
}
