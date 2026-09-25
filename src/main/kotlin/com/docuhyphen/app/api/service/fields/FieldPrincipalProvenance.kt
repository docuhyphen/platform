package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef

/**
 * Who changed a Fields row, expressed as the canonical principal plus the non-secret reference to
 * the access session the change was made in. This is the only authorship the Fields engine records;
 * it deliberately reuses [PrincipalRef] rather than introducing a second identity model.
 */
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

    /** True when [value] already records exactly this authorship, so rewriting it changes nothing. */
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
