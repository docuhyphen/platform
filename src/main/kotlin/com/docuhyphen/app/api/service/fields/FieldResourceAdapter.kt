package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import java.util.UUID

/**
 * Port implemented by each resource domain that adopts Fields. The Fields engine never touches a
 * resource repository directly; it delegates existence, ownership, authorization, and lifecycle
 * questions to the owning resource service through this adapter.
 * See FIELDS-FEATURE.md "Resource Reference".
 */
interface FieldResourceAdapter
{
    /** Stable resource type code, e.g. "EXCHANGE". Matches Schema Definition target types. */
    val resourceType: String

    /** True if the resource exists and may carry a schema assignment. */
    fun exists(resourceId: UUID): Boolean

    /** The configuration scope that owns the resource (used to validate schema scope match). */
    fun ownerScope(resourceId: UUID): ScopeReference?

    /** Throws [io.quarkus.security.ForbiddenException] if the caller may not view field values. */
    fun authorizeViewFields(resourceId: UUID, principal: PrincipalRef, context: AuthorizationContext)

    /** Throws [io.quarkus.security.ForbiddenException] if the caller may not manage field values. */
    fun authorizeManageFields(resourceId: UUID, principal: PrincipalRef, context: AuthorizationContext)

    /**
     * Whether field values may currently be edited given the resource's lifecycle state. The owning
     * service decides this rule (for Exchanges: only while INITIATED / Draft in the first release).
     */
    fun valuesEditable(resourceId: UUID): Boolean
}
