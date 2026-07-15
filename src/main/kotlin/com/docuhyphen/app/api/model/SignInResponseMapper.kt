package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.MfaSessionDto
import com.docuhyphen.app.api.resource.model.SignInResponse

object SignInResponseMapper
{
    fun toResponse(mfaSession: MfaSessionDto): SignInResponse = SignInResponse(
        message = when (mfaSession.mfaType)
        {
            "GOOGLE_AUTHENTICATOR",
            "MICROSOFT_AUTHENTICATOR" -> "Enter the code from your authenticator app."
            else -> "A verification code has been sent to your email."
        },
        mfaSessionId = mfaSession.id.toString(),
        mfaType = mfaSession.mfaType,
        emailFallbackEnabled = mfaSession.emailFallbackEnabled,
    )
}
