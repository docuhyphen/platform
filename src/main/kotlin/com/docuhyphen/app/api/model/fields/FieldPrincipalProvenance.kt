package com.docuhyphen.app.api.model.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef

data class FieldPrincipalProvenance(
    val principal: PrincipalRef,
    val sessionRef: String? = null,
)
{
    fun recordOn(value: FieldValue)
    {
        value.updatedByPrincipalKind = principal.kind
        value.updatedByPrincipalId = principal.id
        value.updatedBySessionRef = sessionRef
    }

    fun recordOn(assignment: SchemaAssignment)
    {
        assignment.assignedByPrincipalKind = principal.kind
        assignment.assignedByPrincipalId = principal.id
        assignment.assignedBySessionRef = sessionRef
    }

    fun recordOn(revision: FieldValueRevision)
    {
        revision.recordedByPrincipalKind = principal.kind
        revision.recordedByPrincipalId = principal.id
        revision.recordedBySessionRef = sessionRef
    }

    fun matches(value: FieldValue): Boolean =
        value.updatedByPrincipalKind == principal.kind &&
                value.updatedByPrincipalId == principal.id &&
                value.updatedBySessionRef == sessionRef

    companion object
    {
        fun of(principal: PrincipalRef, context: AuthorizationContext) =
            FieldPrincipalProvenance(principal, context.sessionRef)
    }
}
