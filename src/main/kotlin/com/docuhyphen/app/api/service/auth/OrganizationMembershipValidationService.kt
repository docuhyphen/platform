package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.auth.OrganizationMembershipValidationResult
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject

@RequestScoped
class OrganizationMembershipValidationService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val userRoleService: UserRoleService,
)
{
    fun validateForSessionAccess(appUser: AppUser): OrganizationMembershipValidationResult
    {
        val isOrgRole = userRoleService.isOrgMember(appUser.id)

        if (!isOrgRole)
        {
            return OrganizationMembershipValidationResult(valid = true)
        }

        val personId = appUser.person?.id
            ?: return OrganizationMembershipValidationResult(
                valid = false,
                reasonCode = RevocationReasonCode.MEMBERSHIP_INACTIVE,
                message = "Organization membership is inactive",
                orgActive = false,
            )

        val organization = organizationRepository.findByAppUserIdAndPersonId(appUser.id, personId)
            ?: return OrganizationMembershipValidationResult(
                valid = false,
                reasonCode = RevocationReasonCode.MEMBERSHIP_INACTIVE,
                message = "Organization membership is inactive",
                orgActive = false,
            )

        return OrganizationMembershipValidationResult(
            valid = true,
            orgActive = organization.isActive,
        )
    }
}
