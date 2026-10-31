package com.docuhyphen.app.api.exception

class InformationRequestCommandRequestException(
    message: String,
    val reasonCode: String? = null,
) : RuntimeException(message)
