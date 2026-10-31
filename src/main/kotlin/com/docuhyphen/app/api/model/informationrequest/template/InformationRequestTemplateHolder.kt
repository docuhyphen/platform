package com.docuhyphen.app.api.model.informationrequest.template

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import java.util.*

data class InformationRequestTemplateHolder(
    val organizationId: UUID?,
    val userId: UUID?,
) {
    fun holds(definition: InformationRequestTemplateDefinition): Boolean =
        when (definition.scopeKind) {
            InformationRequestTemplateScopeKind.PLATFORM -> true
            InformationRequestTemplateScopeKind.ORGANIZATION ->
                organizationId != null && definition.scopeOrgId == organizationId

            InformationRequestTemplateScopeKind.PERSONAL -> userId != null && definition.scopeUserId == userId
        }

    companion object {
        val PLATFORM_ONLY = InformationRequestTemplateHolder(null, null)

        fun organization(organizationId: UUID?) = InformationRequestTemplateHolder(organizationId, null)

        fun person(userId: UUID?) = InformationRequestTemplateHolder(null, userId)

        fun of(exchange: Exchange): InformationRequestTemplateHolder =
            exchange.ownerOrganizationId?.let(::organization) ?: person(exchange.ownerUserId)
    }
}
