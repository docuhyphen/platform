package com.docuhyphen.app.api.service.audit

class AuditDraftInvalidException(errors: List<String>) :
    RuntimeException("Invalid audit event draft: ${errors.joinToString("; ")}")

class AuditCaptureFailedException(message: String, cause: Throwable?) : RuntimeException(message, cause)
