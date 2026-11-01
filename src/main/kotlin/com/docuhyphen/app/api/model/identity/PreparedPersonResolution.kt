package com.docuhyphen.app.api.model.identity

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExternalIdentityResolution
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.organization.TrustedExchangePolicyValidation

data class PreparedPersonResolution(
    val resolution: ExternalIdentityResolution,
    val appUser: AppUser,
    val membership: OrganizationMembership,
    val validation: TrustedExchangePolicyValidation,
)
