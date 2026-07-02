package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import java.util.UUID

/** Canonical principal reference: `(kind, id)`. */
data class PrincipalRef(val kind: PrincipalKind, val id: UUID)
{
    companion object
    {
        fun user(id: UUID)         = PrincipalRef(PrincipalKind.USER, id)
        fun participant(id: UUID)  = PrincipalRef(PrincipalKind.PARTICIPANT, id)
        fun group(id: UUID)        = PrincipalRef(PrincipalKind.PRINCIPAL_GROUP, id)
        fun organization(id: UUID) = PrincipalRef(PrincipalKind.ORGANIZATION, id)
        fun application(id: UUID)  = PrincipalRef(PrincipalKind.APPLICATION, id)
        fun service(id: UUID)      = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, id)
        fun publicLink(id: UUID)   = PrincipalRef(PrincipalKind.PUBLIC_LINK, id)
    }
}

/** Canonical resource reference: `(type, id)`. */
data class ResourceRef(val type: ResourceType, val id: UUID)
{
    companion object
    {
        fun exchange(id: UUID)            = ResourceRef(ResourceType.EXCHANGE, id)
        fun document(id: UUID)            = ResourceRef(ResourceType.DOCUMENT, id)
        fun group(id: UUID)               = ResourceRef(ResourceType.PRINCIPAL_GROUP, id)
        fun docLibrary(id: UUID)          = ResourceRef(ResourceType.DOC_LIBRARY, id)
        fun blueprint(id: UUID)           = ResourceRef(ResourceType.BLUEPRINT, id)
        fun workflowDefinition(id: UUID)  = ResourceRef(ResourceType.WORKFLOW_DEFINITION, id)
        fun sequence(id: UUID)            = ResourceRef(ResourceType.SEQUENCE, id)
        fun variable(id: UUID)            = ResourceRef(ResourceType.VARIABLE, id)
        fun communication(id: UUID)       = ResourceRef(ResourceType.COMMUNICATION, id)
        fun organization(id: UUID)        = ResourceRef(ResourceType.ORGANIZATION, id)
    }
}

