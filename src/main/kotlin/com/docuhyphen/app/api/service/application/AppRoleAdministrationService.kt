package com.docuhyphen.app.api.service.application

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.AppRoleDtoMapper
import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.resource.model.AppAdminDto
import com.docuhyphen.app.api.resource.model.AppUserSearchResultDto
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AppRoleAdministrationService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val appRoleAssignmentService: AppRoleAssignmentService,
    private val appUserService: AppUserService,
)
{
    fun listAppAdmins(): List<AppAdminDto>
    {
        val actorId = requireAppAdmin("APP_ADMIN_LIST")
        return appRoleAssignmentService.listAppAdmins(actorId).map { assignment ->
            AppRoleDtoMapper.toAppAdminDto(assignment, appUserService.getById(assignment.appUserId))
        }
    }

    fun grantAppAdmin(appUserId: String?): AppAdminDto
    {
        val actorId = requireAppAdmin("APP_ADMIN_GRANT")
        val targetId = appUserId?.trim()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrElse { throw IllegalArgumentException("Invalid appUserId") } }
            ?: throw IllegalArgumentException("appUserId is required")
        val assignment = appRoleAssignmentService.grantAppRole(targetId, AppRoleName.APP_ADMIN, actorId)
        return AppRoleDtoMapper.toGrantedAppAdminDto(assignment)
    }

    fun revokeAppAdmin(assignmentId: String)
    {
        val actorId = requireAppAdmin("APP_ADMIN_REVOKE")
        val id = runCatching { UUID.fromString(assignmentId) }
            .getOrElse { throw IllegalArgumentException("Invalid assignmentId") }
        appRoleAssignmentService.revokeAppRole(id, actorId)
    }

    fun searchAppAdminCandidates(query: String?, limit: Int): List<AppUserSearchResultDto>
    {
        val actorId = requireAppAdmin("APP_ADMIN_CANDIDATE_SEARCH")
        return appRoleAssignmentService.searchAppAdminCandidates(query?.trim().orEmpty(), limit, actorId)
            .map(AppRoleDtoMapper::toCandidateDto)
    }

    private fun requireAppAdmin(action: String): UUID
    {
        val actor = authTokenContext.authToken.appUser ?: throw UnauthorizedException("Authentication required")
        appRoleAssignmentService.requireAppAdmin(actor.id, action)
        return actor.id
    }
}
