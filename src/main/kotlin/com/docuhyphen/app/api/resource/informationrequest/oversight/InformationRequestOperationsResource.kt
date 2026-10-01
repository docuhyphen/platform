package com.docuhyphen.app.api.resource.informationrequest.oversight

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestOperationsDtoMapper
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.oversight.*
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.oversight.operations.InformationRequestOperationsResourceOperations
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestOperationsResource @Inject constructor(
    private val operations: InformationRequestOperationsService,
) : InformationRequestOperationsResourceOperations
{
    @Suppress("LongParameterList")
    override fun queue(
        states: List<String>?,
        exchangeId: String?,
        search: String?,
        assigneeId: String?,
        slaStatuses: List<String>?,
        exceptions: List<String>?,
        exceptionsOnly: Boolean?,
        limit: Int?,
        offset: Int?,
    ): Response
    {
        return try
        {
            val filter = InformationRequestOperationsFilter(
                states = states.orEmpty().map { parse<InformationRequestState>(it, "state") }.toSet(),
                exchangeId = exchangeId?.takeIf { it.isNotBlank() }
                    ?.let { InformationRequestCommandHttp.uuid(it, "exchange id") },
                search = search?.trim()?.takeIf { it.isNotEmpty() },
                assigneeId = assigneeId?.takeIf { it.isNotBlank() }
                    ?.let { InformationRequestCommandHttp.uuid(it, "assignee id") },
                slaStatuses = slaStatuses.orEmpty()
                    .map { parse<InformationRequestSlaStatus>(it, "service level status") }.toSet(),
                exceptions = exceptions.orEmpty().map { parse<InformationRequestOperationsException>(it, "exception") }
                    .toSet(),
                exceptionsOnly = exceptionsOnly == true,
                limit = (limit ?: DEFAULT_OPERATIONS_LIMIT).also {
                    requireRange(
                        it in 1..MAXIMUM_OPERATIONS_LIMIT,
                        "limit"
                    )
                },
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
