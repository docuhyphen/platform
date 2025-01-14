package com.securedocsshare.app.api.model

import kotlinx.serialization.Serializable

@Serializable
data class ResponseError(var errorMessage: String? = "")

@Serializable
data class SignInRequest(var email: String? = null, var password: String? = null)

@Serializable
data class SignInResponse(var message: String)

@Serializable
data class SignInCompletionRequest(
    var otp: String? = null,
    var email: String? = null
)

@Serializable
data class SignInCompletionResponse(var token: String? = null)

@Serializable
data class SignOutRequest(var appUser: AppUser)

@Serializable
data class SignUpCompletionRequest(
    var email: String? = null,
    var otp: String? = null,
    val password: String? = null,
    val confirmationPassword: String? = null
)

@Serializable
data class SignUpCompletionResponse(
    var message: String? = null
)

@Serializable
data class PasswordResetCompletionRequest(
    var email: String? = null,
    var otp: String? = null,
    val password: String? = null,
    val confirmationPassword: String? = null
)

@Serializable
data class SignUpInitiateRequest(var email: String? = null)

@Serializable
data class SignUpInitiateResponse(var message: String? = null)

@Serializable
data class SignUpRegenerationRequest(var email: String? = null)

@Serializable
data class SignUpRegenerationResponse(var message: String? = null)

@Serializable
data class PasswordResetRequest(var email: String? = null)

@Serializable
data class PersonRegistrationRequest(
    var firstName: String? = null,
    var lastName: String? = null,
    var idNumber: String? = null
)

@Serializable
data class PersonRegistrationResponse(var message: String? = null)

@Serializable
data class CompanyRegistrationRequest(
    var name: String? = null,
    var registrationNumber: String? = null
)

@Serializable
data class CompanyRegistrationResponse(var message: String? = null)
