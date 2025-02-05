package com.dochyphen.app.api.model

enum class MultifactorAuthenticationType
{
    SMS,
    EMAIL,
    PASSKEY,
    PASSWORD_RESET
}

enum class MultifactorAuthenticationStatus
{
    PENDING,
    COMPLETED
}
