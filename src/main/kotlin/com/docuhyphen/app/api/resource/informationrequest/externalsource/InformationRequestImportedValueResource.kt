package com.docuhyphen.app.api.resource.informationrequest.externalsource

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestExternalSourceDtoMapper
import com.docuhyphen.app.api.model.informationrequest.externalsource.DecideInformationRequestImportedValueCommand
import com.docuhyphen.app.api.model.informationrequest.externalsource.ProposeInformationRequestImportedValueCommand
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.externalsource.operations.InformationRequestImportedValueResourceOperations
import com.docuhyphen.app.api.resource.model.DecideInformationRequestImportedValueRequest
import com.docuhyphen.app.api.resource.model.ProposeInformationRequestImportedValueRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestImportedValueService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestImportedValueResource @Inject constructor(
    private val importedValues: InformationRequestImportedValueService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestImportedValueResourceOperations
{
    override fun list(id: String): Response
    {
        return try
        {
            val access = accessContextFactory.currentAuthenticated()
            val listed = importedValues.values(requestId(id), access)
            Response.ok(listed.map { InformationRequestExternalSourceDtoMapper.toDto(it, access.principal) }
                .toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request imported value list failed", exception)
        }
    }

    override fun propose(
        id: String,
        request: ProposeInformationRequestImportedValueRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = requestId(id)
            val body = request
                ?: throw InformationRequestCommandRequestException("An imported value names its Requirement, value, source, and provenance")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val view = importedValues.propose(
                ProposeInformationRequestImportedValueCommand(
                    requestId = requestId,
                    requirementId = body.requirementId,
                    resultKey = body.resultKey,
                    valueType = body.valueType,
                    value = body.value,
                    sourceReference = body.sourceReference,
                    confidence = body.confidence,
                    verifiedAt = body.verifiedAt?.toInstant(),
                    expiresAt = body.expiresAt?.toInstant(),
                    provenanceReference = body.provenanceReference,
                    access = access,
                    idempotencyKey = key,
                ),
            )
            Response.status(Response.Status.CREATED)
                .entity(InformationRequestExternalSourceDtoMapper.toDto(view, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request imported value proposal failed",
                exception
            )
        }
    }

    override fun decide(
        id: String,
        valueId: String,
        request: DecideInformationRequestImportedValueRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            val requestId = requestId(id)
            val importedValueId = InformationRequestCommandHttp.uuid(valueId, "imported value id")
            val body =
                request ?: throw InformationRequestCommandRequestException("A decision states its outcome and reason")
            val key = InformationRequestCommandHttp.idempotencyKey(idempotencyKey)
            val access = accessContextFactory.currentAuthenticated()
            val view = importedValues.decide(
                DecideInformationRequestImportedValueCommand(
                    requestId,
                    importedValueId,
                    body.decision,
                    body.reasonCode,
                    access,
                    key
                ),
            )
            Response.status(Response.Status.CREATED)
                .entity(InformationRequestExternalSourceDtoMapper.toDto(view, access.principal)).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request imported value decision failed",
                exception
            )
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestImportedValueResource::class.java)
    }
}
