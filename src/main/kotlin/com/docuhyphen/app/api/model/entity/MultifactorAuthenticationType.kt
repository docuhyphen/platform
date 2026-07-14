package com.docuhyphen.app.api.model.entity

enum class MultifactorAuthenticationType
{
    SMS,
    EMAIL,
    GOOGLE_AUTHENTICATOR,
    MICROSOFT_AUTHENTICATOR,
    PASSKEY,
    PASSWORD_RESET;

    fun isAuthenticator(): Boolean =
        this == GOOGLE_AUTHENTICATOR || this == MICROSOFT_AUTHENTICATOR
}
