package com.docuhyphen.app.api.exception

/**
 * Raised when the public sales-enquiry intake receives more submissions from a single
 * source than the configured window allows, so the caller can be told to retry later.
 */
class SalesEnquiryRateLimitedException(
    message: String,
    val retryAfterSeconds: Long? = null,
) : RuntimeException(message)

/**
 * Raised when a sales enquiry is valid but there is no active platform administrator
 * available to receive it, so the caller can be told the request cannot be routed.
 */
class SalesEnquiryRecipientUnavailableException(
    message: String,
) : RuntimeException(message)

