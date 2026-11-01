package com.docuhyphen.app.api.exception

class SalesEnquiryRateLimitedException(
    message: String,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)

class SalesEnquiryRecipientUnavailableException(
    message: String,
) : RuntimeException(message)

