package com.docuhyphen.app.api.exception

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Thrown by AdminActionGuardService when the current session has not recently
 * passed a real auth challenge (see StepUpAuthService.isFresh()).
 *
 * Extends WebApplicationException so it propagates through resource catch blocks
 * (when they re-throw it) and returns a structured 401 with reasonCode=STEP_UP_REQUIRED
 * that the web client can detect and trigger the re-auth modal.
 */
class StepUpRequiredException(
    val action: String? = null,
    message: String = "Step-up authentication is required for this action",
) : WebApplicationException(
    message,
    Response
        .status(Response.Status.UNAUTHORIZED)
        .type(MediaType.APPLICATION_JSON)
        .entity(
            Json.encodeToString(
                StepUpRequiredErrorBody.serializer(),
                StepUpRequiredErrorBody(
                    errorMessage = message,
                    reasonCode = "STEP_UP_REQUIRED",
                    action = action,
                ),
            )
        )
        .build()
)

@Serializable
data class StepUpRequiredErrorBody(
    val errorMessage: String,
    val reasonCode: String,
    val action: String? = null,
)

