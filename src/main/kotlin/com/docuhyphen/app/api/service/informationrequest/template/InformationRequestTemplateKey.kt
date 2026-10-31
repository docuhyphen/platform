package com.docuhyphen.app.api.service.informationrequest.template

internal object InformationRequestTemplateKey
{
    private val PATTERN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")

    fun normalizeOrNull(candidate: String): String? =
        candidate.trim().lowercase().takeIf(PATTERN::matches)

    fun refusalFor(label: String, candidate: String): String =
        "A $label must be lowercase letters, digits, and inner hyphens, so '$candidate' cannot be one"
}
