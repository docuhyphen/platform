package com.docuhyphen.app.api.model.auth

data class StepUpMfaChallenge(
    val sessionId: String,
    val message: String,
    val mfaType: String,
    val emailFallbackEnabled: Boolean,
)
