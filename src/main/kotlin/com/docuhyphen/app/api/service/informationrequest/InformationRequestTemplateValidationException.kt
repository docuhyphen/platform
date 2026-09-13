package com.docuhyphen.app.api.service.informationrequest

/**
 * Thrown when reusable Information Request Template configuration fails deterministic backend
 * validation.
 *
 * [requirementKey], [sectionKey], and [groupKey] identify the offending part of the authored
 * document when the refusal is about one part of it, so a client can focus the item rather than
 * the whole draft. Resources map this to HTTP 400.
 */
class InformationRequestTemplateValidationException(
    override val message: String,
    val sectionKey: String? = null,
    val requirementKey: String? = null,
    val groupKey: String? = null,
) : RuntimeException(message)
