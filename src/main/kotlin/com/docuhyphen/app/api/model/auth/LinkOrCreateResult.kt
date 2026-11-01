package com.docuhyphen.app.api.model.auth

import com.docuhyphen.app.api.model.entity.AppUser

data class LinkOrCreateResult(
    val appUser: AppUser,
    val isNewUser: Boolean,
    val requiresLinkConfirmation: Boolean = false,
    val linkToken: String? = null,
)
