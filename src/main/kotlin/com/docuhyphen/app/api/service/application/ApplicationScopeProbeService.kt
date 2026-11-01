package com.docuhyphen.app.api.service.application

import com.docuhyphen.app.api.resource.model.ApplicationScopeProbeResponse
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant

@ApplicationScoped
class ApplicationScopeProbeService
{
    fun probe(endpointGroup: String): ApplicationScopeProbeResponse = ApplicationScopeProbeResponse(
        endpointGroup = endpointGroup,
        authorized = true,
        issuedAtEpochMillis = Instant.now().toEpochMilli(),
    )
}
