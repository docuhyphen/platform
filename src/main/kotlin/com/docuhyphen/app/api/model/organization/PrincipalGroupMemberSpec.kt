package com.docuhyphen.app.api.model.organization

import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import java.util.*

data class PrincipalGroupMemberSpec(
    val principalId: UUID,
    val principalKind: PrincipalKind = PrincipalKind.USER,
    val groupRole: PrincipalGroupRoleName = PrincipalGroupRoleName.MEMBER,
)
{
    val appUserId: UUID get() = principalId
}
