package com.docuhyphen.app.api.model.subscription

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy

data class UserSubscriptionPolicyResult(
    val user: AppUser,
    val policy: UserSubscriptionPolicy,
)

data class UserSubscriptionPolicyListResult(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val items: List<UserSubscriptionPolicyResult>,
)
