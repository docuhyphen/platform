package com.docuhyphen.app.api.resource.informationrequest.privacy

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestPrivacyDtoMapper
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestItemCorrectionInput
import com.docuhyphen.app.api.model.informationrequest.privacy.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.privacy.operations.InformationRequestPrivacyRequestResourceOperations
import com.docuhyphen.app.api.resource.model.RecordInformationRequestPrivacyRequestRequest
import com.docuhyphen.app.api.service.informationrequest.privacy.InformationRequestPrivacyService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestPrivacyRequestResource @Inject constructor(
    private val privacy: InformationRequestPrivacyService,
) : InformationRequestPrivacyRequestResourceOperations
{
    override fun record(request: RecordInformationRequestPrivacyRequestRequest?): Response
    {
        return try
        {
            val body = request ?: throw InformationRequestCommandRequestException("A privacy request names its subject, kind, purpose, and policy basis")
            val view = privacy.submit(
                RecordInformationRequestPrivacyRequestCommand(
                    subjectIdentityRefId = InformationRequestCommandHttp.uuid(body.subjectIdentityRefId, "subject identity id"),
                    requestKind = body.requestKind,
                    purposeKey = body.purposeKey,
                    policyBasisKey = body.policyBasisKey,
                    transferRegion = body.transferRegion,
                    correction = body.correction?.let {
                        InformationRequestItemCorrectionInput(
                            submissionItemId = InformationRequestCommandHttp.uuid(it.submissionItemId, "submission item id"),
                            value = it.value,
                            narrative = it.narrative,
                            reasonCode = it.reasonCode,
                        )
                    },
                ),
            )
            Response.status(Response.Status.CREATED).entity(InformationRequestPrivacyDtoMapper.toDto(view)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request privacy request failed", exception)
        }
    }

    override fun list(subjectIdentityRefId: String?): Response
    {
        return try
        {
            val subject = subjectIdentityRefId?.takeIf { it.isNotBlank() }?.let { InformationRequestCommandHttp.uuid(it, "subject identity id") }
            Response.ok(privacy.privacyRequests(subject).map(InformationRequestPrivacyDtoMapper::toDto).toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request privacy request list failed", exception)
        }
    }

    override fun get(privacyRequestId: String): Response
    {
        return try
        {
            val view = privacy.privacyRequest(InformationRequestCommandHttp.uuid(privacyRequestId, "privacy request id"))
            Response.ok(InformationRequestPrivacyDtoMapper.toDto(view)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request privacy request read failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestPrivacyRequestResource::class.java)
    }
}
