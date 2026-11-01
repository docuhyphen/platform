package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.model.dto.ExchangeRecipientInvitationDto
import com.docuhyphen.app.api.resource.exchange.operations.ExchangeRecipientInvitationResourceOperations
import com.docuhyphen.app.api.resource.model.ExchangeRecipientInvitationDecisionRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientInvitationService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class ExchangeRecipientInvitationResource @Inject constructor(
    private val invitationService: ExchangeRecipientInvitationService,
) : ExchangeRecipientInvitationResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeRecipientInvitationResource::class.java)
    }

    override fun listPending(): Response
    {
        return try
        {
            val invitations = invitationService.listPending()
            Response.ok(
                object : GenericEntity<List<ExchangeRecipientInvitationDto>>(invitations)
                {},
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing pending Exchange recipient invitations", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while listing pending invitations"))
                        .build()
                }
            }
        }
    }

    override fun decide(
        recipientId: String,
        request: ExchangeRecipientInvitationDecisionRequest,
    ): Response
    {
        return try
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
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is OrganizationTrustException ->
                    Response.status(CONFLICT)
                        .entity(ResponseError("This trusted participant can no longer accept the invitation"))
                        .build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deciding Exchange recipient invitation", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while deciding the participant invitation"))
                        .build()
                }
            }
        }
    }
}
