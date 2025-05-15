package com.dochyphen.app.api.interceptor

import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.ext.Provider
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

@Provider
@Priority(Priorities.ENTITY_CODER)
class InputSanitizationFilter : ContainerRequestFilter
{

    private val logger = LoggerFactory.getLogger(InputSanitizationFilter::class.java)

    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext)
    {
        // Only sanitize JSON content
        if (requestContext.mediaType?.isCompatible(MediaType.APPLICATION_JSON_TYPE) == true)
        {
            val inputStream = requestContext.entityStream

            if (inputStream.available() > 0)
            {
                // Read the entity stream
                val requestBody = inputStream.readBytes().toString(StandardCharsets.UTF_8)

                // Sanitize the input
                val sanitizedBody = sanitizeInput(requestBody)

                // Replace the entity stream with sanitized content
                requestContext.entityStream = ByteArrayInputStream(
                    sanitizedBody.toByteArray(StandardCharsets.UTF_8)
                )
            }
        }
    }

    private fun sanitizeInput(input: String): String
    {
        return input
    }
}