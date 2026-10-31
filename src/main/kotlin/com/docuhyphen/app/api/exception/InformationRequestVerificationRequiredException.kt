package com.docuhyphen.app.api.exception

import io.quarkus.security.ForbiddenException

class InformationRequestVerificationRequiredException(message: String) : ForbiddenException(message)
