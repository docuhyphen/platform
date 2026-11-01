package com.docuhyphen.app.api.service.security

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.SecurityIncidentDtoMapper
import com.docuhyphen.app.api.resource.model.SecurityIncidentResponse
import com.docuhyphen.app.api.service.auth.UserRoleService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class SecurityIncidentQueryService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val securityIncidentService: SecurityIncidentService,
    private val userRoleService: UserRoleService,
)
{
    fun listRecent(limit: Int, incidentType: String?): List<SecurityIncidentResponse>
    {
        val actor = authTokenContext.authToken.appUser ?: throw UnauthorizedException("Unauthorized")

        if (!userRoleService.isAppAdmin(actor.id))
        {
            throw ForbiddenException("Insufficient privileges")
        }

        return securityIncidentService.findRecent(limit = limit, incidentType = incidentType)
            .map(SecurityIncidentDtoMapper::toDto)
    }
}
