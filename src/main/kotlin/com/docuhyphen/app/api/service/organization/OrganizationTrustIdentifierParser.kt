package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class OrganizationTrustIdentifierParser
{
    fun parse(value: String?, label: String): UUID
    {
        val candidate = value?.takeIf { it.isNotBlank() }
            ?: throw OrganizationTrustValidationException("$label ID is required")
        return try
        {
            UUID.fromString(candidate)
        }
        catch (exception: IllegalArgumentException)
        {
            throw OrganizationTrustValidationException("$label ID is invalid")
        }
    }
}
