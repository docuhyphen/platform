package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.command.CommandActorRef
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.command.CommandResultReference
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import java.util.*

internal object InformationRequestTemplateCommandReceipts {
    const val CREATE = "create-information-request-template"
    const val PUBLISH = "publish-information-request-template"
    const val NEW_VERSION = "create-information-request-template-version"
    const val RETIRE = "retire-information-request-template-version"
    const val CLONE = "clone-information-request-template"

    fun forTemplate(
        templateId: UUID,
        operation: String,
        actor: PrincipalRef,
        idempotencyKey: String,
        vararg facts: Any?,
    ): CommandReceiptRequest =
        request(
            ResourceRef(ResourceType.INFORMATION_REQUEST_TEMPLATE, templateId),
            operation,
            actor,
            idempotencyKey,
            facts.toList()
        )

    fun forOwner(
        scopeKind: InformationRequestTemplateScopeKind,
        ownerId: UUID?,
        operation: String,
        actor: PrincipalRef,
        idempotencyKey: String,
        vararg facts: Any?,
    ): CommandReceiptRequest =
        request(
            ResourceRef(
                ResourceType.INFORMATION_REQUEST_TEMPLATE,
                UUID.nameUUIDFromBytes(
                    "${ResourceType.INFORMATION_REQUEST_TEMPLATE.name}|${scopeKind.name}|${
                        ownerId?.toString().orEmpty()
                    }"
                        .toByteArray(Charsets.UTF_8),
                ),
            ),
            operation,
            actor,
            idempotencyKey,
            facts.toList(),
        )

    fun result(templateId: UUID, revision: Long? = null): CommandResultReference =
        CommandResultReference(ResourceType.INFORMATION_REQUEST_TEMPLATE, templateId, revision)

    private fun request(
        resource: ResourceRef,
        operation: String,
        actor: PrincipalRef,
        idempotencyKey: String,
        facts: List<Any?>,
    ) = CommandReceiptRequest(
        resource = resource,
        operation = operation,
        actor = CommandActorRef.principal(actor),
        idempotencyKey = idempotencyKey,
        requestFingerprint = CommandRequestFingerprint.of(operation, resource.id, *facts.toTypedArray()),
    )
}
