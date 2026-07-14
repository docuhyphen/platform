package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.AuthenticatorEnrollmentDto
import com.docuhyphen.app.api.model.dto.MfaConfigurationDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthenticatorEnrollment

object AuthenticatorMfaDtoMapper
{
    fun toConfigurationDto(appUser: AppUser): MfaConfigurationDto = MfaConfigurationDto(
        method = appUser.mfaType.name,
        authenticatorConfigured = appUser.mfaType.isAuthenticator() &&
            !appUser.authenticatorSecretEncrypted.isNullOrBlank(),
        emailFallbackEnabled = appUser.emailMfaFallbackEnabled,
    )

    fun toEnrollmentDto(
        enrollment: AuthenticatorEnrollment,
        secret: String,
        otpauthUri: String,
    ): AuthenticatorEnrollmentDto = AuthenticatorEnrollmentDto(
        id = enrollment.id.toString(),
        provider = enrollment.provider.name,
        secret = secret,
        otpauthUri = otpauthUri,
        expiresAt = enrollment.expiresAt.toInstant().toString(),
    )
}
