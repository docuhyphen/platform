package com.docuhyphen.app.api.resource.informationrequest.submission

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.submission.handler.InformationRequestSubmissionRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.submission.operations.InformationRequestSubmissionResourceOperations
import com.docuhyphen.app.api.resource.model.SubmitInformationRequestPackageRequest
import com.docuhyphen.app.api.resource.model.WithdrawInformationRequestPackageRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestSubmissionResource @Inject constructor(
    submissionService: InformationRequestSubmissionService,
    queryService: InformationRequestSubmissionQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestSubmissionResourceOperations
{
    private val handler = InformationRequestSubmissionRequestHandler(submissionService, queryService)

    override fun list(id: String): Response
    {
        return try
        {
            handler.list(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission list failed", exception)
        }
    }

    override fun detail(id: String, packageId: String): Response
    {
        return try
        {
            handler.detail(requestId(id), packageId(packageId), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission lookup failed", exception)
        }
    }

    override fun submit(
        id: String,
        request: SubmitInformationRequestPackageRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.submit(
                requestId(id),
                request?.stageKey,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission failed", exception)
        }
    }

    override fun withdraw(
        id: String,
        packageId: String,
        request: WithdrawInformationRequestPackageRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.withdraw(
                requestId(id),
                packageId(packageId),
                request?.reasonCode,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission withdrawal failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private fun packageId(raw: String) = InformationRequestCommandHttp.uuid(raw, "submission package id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSubmissionResource::class.java)
    }
}
