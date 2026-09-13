package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import java.util.UUID

/**
 * Who changed a Fields row, expressed as the canonical principal plus the non-secret reference to
 * the access session the change was made in. This is the only authorship the Fields engine records;
 * it deliberately reuses [PrincipalRef] rather than introducing a second identity model.
 *
 * The registered-user columns that predate the canonical pair are foreign keys into the
 * registered-user table, so only a [PrincipalKind.USER] principal can occupy them. Keeping that rule
 * in [legacyAppUserId] means every row that carries authorship applies it the same way, and a
 * participant, public-link, application, or service principal writes the canonical columns only.
 */
data class FieldPrincipalProvenance(
    val principal: PrincipalRef,
    val sessionRef: String? = null,
)
{
    /** The registered user the retained legacy column may name, or null for every other kind. */
    val legacyAppUserId: UUID?
        get() = principal.id.takeIf { principal.kind == PrincipalKind.USER }

    fun recordOn(value: FieldValue)
    {
        value.updatedByPrincipalKind = principal.kind
        value.updatedByPrincipalId = principal.id
        value.updatedBySessionRef = sessionRef
        value.updatedByAppUserId = legacyAppUserId
    }

    fun recordOn(assignment: SchemaAssignment)
    {
        assignment.assignedByPrincipalKind = principal.kind
        assignment.assignedByPrincipalId = principal.id
        assignment.assignedBySessionRef = sessionRef
        assignment.assignedByAppUserId = legacyAppUserId
    }

    fun recordOn(revision: FieldValueRevision)
    {
        revision.recordedByPrincipalKind = principal.kind
        revision.recordedByPrincipalId = principal.id
        revision.recordedBySessionRef = sessionRef
        revision.recordedByAppUserId = legacyAppUserId
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
