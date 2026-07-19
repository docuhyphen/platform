package com.docuhyphen.app.api.exception

class ExchangeRecipientEligibilityException(
    message: String = "This Exchange can no longer be accepted",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
