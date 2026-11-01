package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.model.dto.StepUpRequiredErrorBody
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.json.Json

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
