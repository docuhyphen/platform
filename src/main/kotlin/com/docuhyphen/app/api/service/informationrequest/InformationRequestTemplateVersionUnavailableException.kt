package com.docuhyphen.app.api.service.informationrequest

/**
 * Thrown when an exact Information Request Template Version cannot serve the purpose it was named
 * for.
 *
 * [code] is a stable machine reason rather than prose, because the two refusals mean different
 * things to an author: a Version that was never publishable was mis-selected, while a Version that
 * has since been retired was a valid choice that has to be made again. Resources map this to
 * HTTP 400.
 */
class InformationRequestTemplateVersionUnavailableException(
    val code: String,
    override val message: String,
) : IllegalArgumentException(message)
{
    companion object
    {
        /** No Version with that identity exists. */
        const val NOT_FOUND = "INFORMATION_REQUEST_TEMPLATE_VERSION_NOT_FOUND"

        /** The Version is still being authored, so its configuration can still change. */
        const val NOT_PUBLISHED = "INFORMATION_REQUEST_TEMPLATE_VERSION_NOT_PUBLISHED"

        /** The Version was withdrawn from new use. What was already created from it is unaffected. */
        const val RETIRED = "INFORMATION_REQUEST_TEMPLATE_VERSION_RETIRED"
    }
}

