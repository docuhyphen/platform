package com.docuhyphen.app.api.service.application

import com.docuhyphen.app.api.model.ApplicationDtoMapper
import com.docuhyphen.app.api.model.application.CreateApplicationRequest
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.dto.ApplicationCreatedDto
import com.docuhyphen.app.api.model.dto.ApplicationDto
import com.docuhyphen.app.api.model.dto.RotateCredentialsResponse
import com.docuhyphen.app.api.model.entity.ApplicationType
import com.docuhyphen.app.api.resource.model.CreateApplicationRequestBody
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class ApplicationAdministrationService @Inject constructor(
    private val applicationManagementService: ApplicationManagementService,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    fun listApplications(): List<ApplicationDto> =
        applicationManagementService.listAll(principal(), context()).map(ApplicationDtoMapper::toDto)

    fun createApplication(body: CreateApplicationRequestBody): ApplicationCreatedDto
    {
        val name = body.name
        require(!name.isNullOrBlank()) { "name is required" }
        val request = CreateApplicationRequest(
            name = name,
            description = body.description,
            applicationType = parseApplicationType(body.applicationType),
            ownerOrganizationId = body.ownerOrganizationId?.let { UUID.fromString(it) },
            grantedCapabilities = body.grantedCapabilities ?: emptyList(),
        )
        val (application, rawSecret) = applicationManagementService.create(request, principal(), context())
        return ApplicationDtoMapper.toCreatedDto(application, rawSecret)
    }

    fun getApplication(id: UUID): ApplicationDto =
        ApplicationDtoMapper.toDto(applicationManagementService.getById(id, principal(), context()))

    fun rotateCredentials(id: UUID): RotateCredentialsResponse =
        ApplicationDtoMapper.toRotateCredentialsResponse(
            applicationManagementService.rotateCredentials(id, principal(), context())
        )

    fun updateCapabilities(id: UUID, capabilities: List<String>?)
    {
        applicationManagementService.updateGrantedCapabilities(id, capabilities ?: emptyList(), principal(), context())
    }

    fun deactivate(id: UUID)
    {
        applicationManagementService.deactivate(id, principal(), context())
    }

    private fun parseApplicationType(raw: String?): ApplicationType =
        raw?.trim()?.uppercase()?.let { runCatching { ApplicationType.valueOf(it) }.getOrNull() }
            ?: ApplicationType.SERVICE

    private fun principal(): PrincipalRef =
        authorizationContextFactory.currentPrincipal() ?: throw SecurityException("Authentication required")

    private fun context(): AuthorizationContext = authorizationContextFactory.currentContext()
}
