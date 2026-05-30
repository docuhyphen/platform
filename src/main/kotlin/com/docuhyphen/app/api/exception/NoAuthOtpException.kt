package com.docuhyphen.app.api.exception

class NoAuthOtpException(
    message: String,
    val reasonCode: String,
    val retryAfterSeconds: Long? = null,
) : IllegalArgumentException(message)

