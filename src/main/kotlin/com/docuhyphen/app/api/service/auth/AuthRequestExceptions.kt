package com.docuhyphen.app.api.service.auth

class AuthRequestRateLimitedException(
    message: String = "Too many requests. Please try again later.",
) : RuntimeException(message)

class CsrfValidationFailedException : RuntimeException("CSRF validation failed")
