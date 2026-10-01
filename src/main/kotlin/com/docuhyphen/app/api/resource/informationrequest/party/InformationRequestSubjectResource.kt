package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.party.operations.InformationRequestSubjectResourceOperations
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestSubjectService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestSubjectResource @Inject constructor(
    private val subjectService: InformationRequestSubjectService,
) : InformationRequestSubjectResourceOperations
{
    override fun list(): Response
    {
        return try
        {
            Response.ok(subjectService.listForOwner().toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request subject list failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSubjectResource::class.java)
    }
}
