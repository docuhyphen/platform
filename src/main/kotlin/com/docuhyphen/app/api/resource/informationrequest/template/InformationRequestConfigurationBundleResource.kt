package com.docuhyphen.app.api.resource.informationrequest.template

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestConfigurationBundleDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.template.operations.InformationRequestConfigurationBundleResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestConfigurationBundleValidator
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestConfigurationBundleResource @Inject constructor(
    private val validator: InformationRequestConfigurationBundleValidator,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestConfigurationBundleResourceOperations
{
    override fun validate(document: String?): Response
    {
        return try
        {
            accessContextFactory.currentAuthenticated()
            val body = document?.takeIf { it.isNotBlank() }
                ?: throw InformationRequestCommandRequestException("A validation names the bundle document to check")
            Response.ok(InformationRequestConfigurationBundleDtoMapper.toDto(validator.check(body))).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request configuration bundle validation failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestConfigurationBundleResource::class.java)
    }
}
