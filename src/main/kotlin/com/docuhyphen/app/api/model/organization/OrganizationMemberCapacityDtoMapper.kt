package com.docuhyphen.app.api.model.organization

import com.docuhyphen.app.api.model.dto.OrgMemberCapacityDto
import com.docuhyphen.app.api.resource.model.OrgMemberCapacityResponse

object OrganizationMemberCapacityDtoMapper
{
    private const val NEAR_CAPACITY_RATIO = 0.8

    fun toResponse(capacity: OrgMemberCapacityDto): OrgMemberCapacityResponse
    {
        val maxUsers = capacity.maxUsers
        return OrgMemberCapacityResponse(
            organizationId = capacity.organizationId.toString(),
            tierCode = capacity.tierCode,
            maxUsers = maxUsers,
            activeUsers = capacity.activeUsers,
            atCap = maxUsers != null && capacity.activeUsers >= maxUsers,
            nearCap = maxUsers != null && capacity.activeUsers >= (maxUsers * NEAR_CAPACITY_RATIO).toLong(),
        )
    }
}
