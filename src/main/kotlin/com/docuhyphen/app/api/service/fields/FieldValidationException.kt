package com.docuhyphen.app.api.service.fields

/**
 * Thrown when a field value or configuration fails deterministic backend validation.
 * [fieldKey] identifies the offending field when known. Resources map this to HTTP 400.
 */
class FieldValidationException(
    override val message: String,
    val fieldKey: String? = null,
) : RuntimeException(message)
