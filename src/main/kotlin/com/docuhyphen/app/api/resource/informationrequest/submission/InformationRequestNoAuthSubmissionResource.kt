package com.docuhyphen.app.api.resource.informationrequest.submission

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.submission.handler.InformationRequestSubmissionRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.submission.operations.InformationRequestNoAuthSubmissionResourceOperations
import com.docuhyphen.app.api.resource.model.SubmitInformationRequestPackageRequest
import com.docuhyphen.app.api.resource.model.WithdrawInformationRequestPackageRequest
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoAuthSubmissionResource @Inject constructor(
    submissionService: InformationRequestSubmissionService,
    queryService: InformationRequestSubmissionQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthSubmissionResourceOperations
{
    private val handler = InformationRequestSubmissionRequestHandler(submissionService, queryService)

    override fun list(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.list(requestId, access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission list failed", exception)
        }
    }

    override fun detail(
        id: String,
        packageId: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.detail(requestId, packageId(packageId), access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission lookup failed", exception)
        }
    }

    override fun submit(
        id: String,
        request: SubmitInformationRequestPackageRequest?,
        accessLinkToken: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.submit(requestId, request?.stageKey, access, ifMatch, idempotencyKey)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission failed", exception)
        }
    }

    override fun withdraw(
        id: String,
        packageId: String,
        request: WithdrawInformationRequestPackageRequest?,
        accessLinkToken: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.withdraw(requestId, packageId(packageId), request?.reasonCode, access, ifMatch, idempotencyKey)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission withdrawal failed", exception)
        }
    }

    private fun packageId(raw: String) = InformationRequestCommandHttp.uuid(raw, "submission package id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthSubmissionResource::class.java)
    }
}
