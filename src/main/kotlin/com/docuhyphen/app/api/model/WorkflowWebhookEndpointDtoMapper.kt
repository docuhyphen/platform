package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.application.WebhookSigningSecret
import com.docuhyphen.app.api.model.dto.WebhookEndpointCreatedDto
import com.docuhyphen.app.api.model.dto.WebhookEndpointDto
import com.docuhyphen.app.api.model.dto.WebhookSigningSecretRotatedDto
import com.docuhyphen.app.api.model.entity.WorkflowWebhookEndpoint

object WorkflowWebhookEndpointDtoMapper
{
    fun toDto(endpoint: WorkflowWebhookEndpoint): WebhookEndpointDto = WebhookEndpointDto(
        id = endpoint.id.toString(),
        ownerOrganizationId = endpoint.ownerOrganizationId.toString(),
        workflowDefinitionId = endpoint.workflowDefinitionId.toString(),
        registeredApplicationId = endpoint.registeredApplicationId?.toString(),
        targetUrl = endpoint.targetUrl,
        isEnabled = endpoint.isEnabled,
        signingSecretVersion = endpoint.signingSecretVersion,
        permittedEventTypes = endpoint.permittedEventTypes,
        createdDate = endpoint.createdDate.toInstant().toString(),
        updatedDate = endpoint.updatedDate.toInstant().toString(),
    )

    fun toCreatedDto(endpoint: WorkflowWebhookEndpoint, secret: WebhookSigningSecret): WebhookEndpointCreatedDto =
        WebhookEndpointCreatedDto(
            id = endpoint.id.toString(),
            targetUrl = endpoint.targetUrl,
            rawSigningSecret = secret.rawSecret,
            signingSecretVersion = secret.version,
        )

    fun toRotatedDto(secret: WebhookSigningSecret): WebhookSigningSecretRotatedDto =
        WebhookSigningSecretRotatedDto(secret.rawSecret, secret.version)
}
