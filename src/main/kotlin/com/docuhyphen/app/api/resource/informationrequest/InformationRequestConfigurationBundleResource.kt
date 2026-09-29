package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestConfigurationBundleDtoMapper
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConfigurationBundleValidator
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-request-configuration-bundles/validations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestConfigurationBundleResource @Inject constructor(
    private val validator: InformationRequestConfigurationBundleValidator,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    @POST
    fun validate(document: String?): Response
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
