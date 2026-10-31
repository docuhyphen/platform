package com.docuhyphen.app.api.service.informationrequest.template

class InformationRequestTemplateVersionUnavailableException(
    val code: String,
    override val message: String,
) : IllegalArgumentException(message)
{
    companion object
    {
        const val NOT_FOUND = "INFORMATION_REQUEST_TEMPLATE_VERSION_NOT_FOUND"

        const val NOT_PUBLISHED = "INFORMATION_REQUEST_TEMPLATE_VERSION_NOT_PUBLISHED"

        const val RETIRED = "INFORMATION_REQUEST_TEMPLATE_VERSION_RETIRED"

        const val PLATFORM_COPY_REQUIRED = "INFORMATION_REQUEST_TEMPLATE_VERSION_PLATFORM_COPY_REQUIRED"

        const val OWNER_MISMATCH = "INFORMATION_REQUEST_TEMPLATE_VERSION_OWNER_MISMATCH"

        const val SCHEMA_UNAVAILABLE = "INFORMATION_REQUEST_TEMPLATE_VERSION_SCHEMA_UNAVAILABLE"

        const val BLUEPRINT_UNAVAILABLE = "INFORMATION_REQUEST_BLUEPRINT_UNAVAILABLE"
    }
}

