package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestOperationsDtoMapper
import com.docuhyphen.app.api.model.informationrequest.DEFAULT_OPERATIONS_LIMIT
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsException
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsFilter
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSlaStatus
import com.docuhyphen.app.api.model.informationrequest.MAXIMUM_OPERATIONS_LIMIT
import com.docuhyphen.app.api.service.informationrequest.InformationRequestOperationsService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-request-operations")
@Produces(APPLICATION_JSON)
class InformationRequestOperationsResource @Inject constructor(
    private val operations: InformationRequestOperationsService,
)
{
    @GET
    @Suppress("LongParameterList")
    fun queue(
        @QueryParam("state") states: List<String>?,
        @QueryParam("exchangeId") exchangeId: String?,
        @QueryParam("slaStatus") slaStatuses: List<String>?,
        @QueryParam("exception") exceptions: List<String>?,
        @QueryParam("exceptionsOnly") exceptionsOnly: Boolean?,
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
    ): Response
    {
        return try
        {
            val filter = InformationRequestOperationsFilter(
                states = states.orEmpty().map { parse<InformationRequestState>(it, "state") }.toSet(),
                exchangeId = exchangeId?.takeIf { it.isNotBlank() }?.let { InformationRequestCommandHttp.uuid(it, "exchange id") },
                slaStatuses = slaStatuses.orEmpty().map { parse<InformationRequestSlaStatus>(it, "service level status") }.toSet(),
                exceptions = exceptions.orEmpty().map { parse<InformationRequestOperationsException>(it, "exception") }.toSet(),
                exceptionsOnly = exceptionsOnly == true,
                limit = (limit ?: DEFAULT_OPERATIONS_LIMIT).also { requireRange(it in 1..MAXIMUM_OPERATIONS_LIMIT, "limit") },
                offset = (offset ?: 0).also { requireRange(it >= 0, "offset") },
            )
            Response.ok(InformationRequestOperationsDtoMapper.toDto(operations.queue(filter))).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request operations queue failed", exception)
        }
    }

    private inline fun <reified T : Enum<T>> parse(raw: String, name: String): T =
        enumValues<T>().firstOrNull { it.name == raw.trim().uppercase() }
            ?: throw InformationRequestCommandRequestException("Unknown $name: $raw")

    private fun requireRange(valid: Boolean, name: String)
    {
        if (!valid) throw InformationRequestCommandRequestException("The $name is out of range")
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestOperationsResource::class.java)
    }
}
