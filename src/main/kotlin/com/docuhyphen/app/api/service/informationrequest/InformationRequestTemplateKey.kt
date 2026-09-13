package com.docuhyphen.app.api.service.informationrequest

/**
 * The shape of a stable machine identifier in reusable request configuration.
 *
 * Template namespaces and keys, section keys, and requirement keys are all identifiers that outlive
 * the wording around them: a condition names one, a recorded response means one, and an occurrence
 * path is built from them. They therefore have to mean the same thing however they were typed, and
 * one rule states that for all of them rather than each authoring path stating its own.
 *
 * Keys into the owner's own vocabulary are deliberately not held to this. A confidentiality
 * compartment, a condition rule, and an occurrence anchor are the customer's names for their own
 * things, and production logic never interprets them.
 */
internal object InformationRequestTemplateKey
{
    private val PATTERN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")

    /** The single reading of [candidate], or null when it cannot be a machine identifier at all. */
    fun normalizeOrNull(candidate: String): String? =
        candidate.trim().lowercase().takeIf(PATTERN::matches)

    fun refusalFor(label: String, candidate: String): String =
        "A $label must be lowercase letters, digits, and inner hyphens, so '$candidate' cannot be one"
}
