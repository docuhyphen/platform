package com.docuhyphen.app.api.resource.informationrequest.parent

import com.docuhyphen.app.api.model.InformationRequestSummaryDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.parent.operations.InformationRequestAssignedWorkResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestAssignedWorkService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestAssignedWorkResource @Inject constructor(
    private val assignedWork: InformationRequestAssignedWorkService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestAssignedWorkResourceOperations {
    override fun list(): Response {
        return try {
            val work = assignedWork.listFor(accessContextFactory.currentAuthenticated())
            Response.ok(work.map(InformationRequestSummaryDtoMapper::toDto).toTypedArray()).build()
        } catch (exception: Exception) {
            InformationRequestCommandHttp.refused(logger, "Assigned Information Request listing failed", exception)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(InformationRequestAssignedWorkResource::class.java)
    }
}
