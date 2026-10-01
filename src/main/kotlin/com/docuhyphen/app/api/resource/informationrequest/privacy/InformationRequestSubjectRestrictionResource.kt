package com.docuhyphen.app.api.resource.informationrequest.privacy

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestPrivacyDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.privacy.operations.InformationRequestSubjectRestrictionResourceOperations
import com.docuhyphen.app.api.resource.model.LiftInformationRequestSubjectRestrictionRequest
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestSubjectRestrictionService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestSubjectRestrictionResource @Inject constructor(
    private val restrictions: InformationRequestSubjectRestrictionService,
) : InformationRequestSubjectRestrictionResourceOperations
{
    override fun list(): Response
    {
        return try
        {
            Response.ok(restrictions.restrictions().map(InformationRequestPrivacyDtoMapper::toDto).toTypedArray())
                .build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request subject restriction list failed",
                exception
            )
        }
    }

    override fun lift(restrictionId: String, request: LiftInformationRequestSubjectRestrictionRequest?): Response
    {
        return try
        {
            val body =
                request ?: throw InformationRequestCommandRequestException("Lifting a restriction states its reason")
            val lifted =
                restrictions.lift(InformationRequestCommandHttp.uuid(restrictionId, "restriction id"), body.reasonCode)
            Response.ok(InformationRequestPrivacyDtoMapper.toDto(lifted)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request subject restriction lift failed",
                exception
            )
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSubjectRestrictionResource::class.java)
    }
}
