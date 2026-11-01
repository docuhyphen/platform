package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.SecurityIncident
import com.docuhyphen.app.api.resource.model.SecurityIncidentResponse

object SecurityIncidentDtoMapper
{
    fun toDto(incident: SecurityIncident): SecurityIncidentResponse = SecurityIncidentResponse(
        id = incident.id.toString(),
        incidentType = incident.incidentType,
        severity = incident.severity,
        actorId = incident.actorId?.toString(),
        requestId = incident.requestId,
        details = incident.details,
        createdDate = incident.createdDate.toInstant().toString(),
    )
}
