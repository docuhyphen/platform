package com.docuhyphen.app.api.service.fields

class FieldValidationException(
    override val message: String,
    val fieldKey: String? = null,
) : RuntimeException(message)
