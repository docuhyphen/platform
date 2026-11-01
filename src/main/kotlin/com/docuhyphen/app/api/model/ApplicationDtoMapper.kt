package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.application.RotateCredentialsResult
import com.docuhyphen.app.api.model.dto.ApplicationCreatedDto
import com.docuhyphen.app.api.model.dto.ApplicationDto
import com.docuhyphen.app.api.model.dto.RotateCredentialsResponse
import com.docuhyphen.app.api.model.entity.Application

object ApplicationDtoMapper
{
    fun toDto(application: Application): ApplicationDto = ApplicationDto(
        id = application.id.toString(),
        name = application.name,
        description = application.description,
        applicationType = application.applicationType.name,
        ownerOrganizationId = application.ownerOrganizationId?.toString(),
        isActive = application.isActive,
        grantedCapabilities = application.grantedCapabilitiesJson,
        createdDate = application.createdDate.toInstant().toString(),
        lastAccessDate = application.lastAccessDate?.toInstant()?.toString(),
    )

    fun toCreatedDto(application: Application, rawSecret: String): ApplicationCreatedDto = ApplicationCreatedDto(
        id = application.id.toString(),
        name = application.name,
        description = application.description,
        applicationType = application.applicationType.name,
        ownerOrganizationId = application.ownerOrganizationId?.toString(),
        apiKey = application.apiKey,
        rawSecret = rawSecret,
        grantedCapabilities = application.grantedCapabilitiesJson,
        createdDate = application.createdDate.toInstant().toString(),
    )

    fun toRotateCredentialsResponse(result: RotateCredentialsResult): RotateCredentialsResponse =
        RotateCredentialsResponse(result.apiKey, result.rawSecret)
}
