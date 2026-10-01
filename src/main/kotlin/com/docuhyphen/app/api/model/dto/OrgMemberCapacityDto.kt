package com.docuhyphen.app.api.model.dto

import java.util.*

data class OrgMemberCapacityDto(
    val organizationId: UUID,
    val tierCode: String,
    val maxUsers: Long?,
    val activeUsers: Long,
)
