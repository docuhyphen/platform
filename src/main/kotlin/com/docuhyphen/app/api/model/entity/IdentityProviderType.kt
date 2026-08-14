package com.docuhyphen.app.api.model.entity

enum class IdentityProviderType(val displayName: String)
{
    INTERNAL("Email & Password"),
    MICROSOFT("Microsoft"),
    GOOGLE("Google")
}

