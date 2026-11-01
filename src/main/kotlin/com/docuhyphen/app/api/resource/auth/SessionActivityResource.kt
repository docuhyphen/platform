package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.resource.auth.operations.SessionActivityResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.SessionActivityService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class SessionActivityResource @Inject constructor(
    private val sessionActivityService: SessionActivityService,
) : SessionActivityResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SessionActivityResource::class.java)
    }

    override fun recordActivity(): Response
    {
        return try
        {
            Response.ok(sessionActivityService.recordActivity()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                {
                    logger.warn("Rejected session activity update", exception)
                    Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ResponseError("Session is no longer active"))
                        .build()
                }

                else ->
                {
                    logger.error("Error recording session activity", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to record session activity"))
                        .build()
                }
            }
        }
    }
}
