package com.docuhyphen.app.api.resource.organization.model

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.resource.organization.PlatformOrganizationResource

internal data class PlatformOrganizationHarness(
    val resource: PlatformOrganizationResource,
    val organizationRepository: OrganizationRepository,
)

data class PrincipalCase(
    val label: String,
    val appRoles: Set<AppRoleName> = setOf(AppRoleName.APP_USER),
    val organizationRoles: Set<OrganizationRoleName> = emptySet(),
    val assignmentEffective: Boolean = true,
    val userActive: Boolean = true,
    val userDeprovisioned: Boolean = false,
) {
    override fun toString(): String = label
}
