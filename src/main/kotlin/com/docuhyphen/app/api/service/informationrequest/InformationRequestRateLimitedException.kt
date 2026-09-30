package com.docuhyphen.app.api.service.informationrequest

class InformationRequestRateLimitedException(
    val reasonCode: String,
    val retryAfterSeconds: Long,
    message: String,
) : RuntimeException(message)
