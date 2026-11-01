package com.docuhyphen.app.api.service.subscription

class SubscriptionTrialRequestConflictException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
