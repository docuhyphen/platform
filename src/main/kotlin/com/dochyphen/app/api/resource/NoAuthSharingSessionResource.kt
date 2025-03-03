package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.model.BasicModelConverter
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.UpdateNoAuthSharingSession
import com.dochyphen.app.api.service.sharingsession.SharingSessionRetrievalService
import com.dochyphen.app.api.service.sharingsession.SharingSessionUpdateService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("no-auth/sharing-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class NoAuthSharingSessionResource @Inject constructor(
    private val sharingSessionRetrievalService: SharingSessionRetrievalService,
    private val sharingSessionUpdateService: SharingSessionUpdateService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(NoAuthSharingSessionResource::class.java)
    }

    @GET
    @Path("/{sessionId}")
    fun getNoAuthSharingSession(@PathParam("sessionId") sessionId: String): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(2000, 4000)

        return try
        {
            val sharingSession = sharingSessionRetrievalService.getNoAuthSharingSession(sessionId)

            Response.ok(BasicModelConverter.toNoAuthDto(sharingSession)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SharingSessionNotFoundException ->
                {
                    logger.error("Error getting sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error getting sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting sharing session", exception)

                    val responseError = ResponseError("An error occurred while getting sharing session")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @PUT
    @Path("/{sessionId}")
    fun updateNoAuthSharingSession(
        @PathParam("sessionId") sessionId: String,
        request: UpdateNoAuthSharingSession
    ): Response
    {
        return try
        {
            val updatedSession = with(request) {
                sharingSessionUpdateService.updateNoAuthSharingSession(sessionId, status, otp, rejectReason)
            }

            Response.ok(BasicModelConverter.toNoAuthDto(updatedSession)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ForbiddenException ->
                {
                    logger.error("Error updating sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.FORBIDDEN)
                        .entity(responseError)
                        .build()
                }

                is SharingSessionNotFoundException ->
                {
                    logger.error("Error adding sharing session document", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error updating sharing session", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error updating sharing session", exception)

                    val responseError = ResponseError("An error occurred while updating sharing session")
                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}