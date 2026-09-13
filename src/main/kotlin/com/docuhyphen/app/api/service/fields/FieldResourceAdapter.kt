package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import java.util.*

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

    /**
     * The per-binding policy governing this resource's Fields. The same policy filters what a caller
     * may be shown and authorizes what a caller may change, so a projection can never disclose a
     * binding the same caller would be refused permission to address.
     */
    val bindingPolicy: FieldBindingPolicy

    /** True if the resource exists and may carry a schema assignment. */
    fun exists(resourceId: UUID): Boolean

    /** The configuration scope that owns the resource (used to validate schema scope match). */
    fun ownerScope(resourceId: UUID): ScopeReference?

    /** Immutable paying subject that owns mutations performed against this resource. */
    fun subscriptionContext(resourceId: UUID): SubscriptionContext?

    /**
     * True when this resource's subscription mutation entitlement was already decided and frozen
     * elsewhere (an issuance-time execution grant, for example) rather than answering to the
     * owner's live subscription on every write. Default false: the caller must still enforce
     * [BusinessFieldsSubscriptionGuard.requireResourceMutation] against [subscriptionContext].
     */
    fun mutationEntitlementFrozen(resourceId: UUID): Boolean = false

    /** Throws [io.quarkus.security.ForbiddenException] if the caller may not view field values. */
    fun authorizeViewFields(resourceId: UUID, principal: PrincipalRef, context: AuthorizationContext)

    /** Throws [io.quarkus.security.ForbiddenException] if the caller may not manage field values. */
    fun authorizeManageFields(resourceId: UUID, principal: PrincipalRef, context: AuthorizationContext)

    /**
     * Throws [io.quarkus.security.ForbiddenException] if the caller may not change which Schema
     * governs the resource. Choosing or removing a Schema reshapes every value the resource can
     * hold, so it is a configuration decision for the resource's owner or administrator rather than
     * part of the ordinary write permission that lets a collaborator fill values in. Each adapter
     * states this capability explicitly; it is deliberately not defaulted to the value-write rule.
     */
    fun authorizeManageSchema(resourceId: UUID, principal: PrincipalRef, context: AuthorizationContext)

    /**
     * Whether the resource is still in a lifecycle state where its governing Schema Assignment may
     * be created, replaced, or removed. Resources that have the same rule for assignment shape and
     * value edits can keep the default.
     */
    fun schemaAssignmentMutable(resourceId: UUID): Boolean = valuesEditable(resourceId)

    /**
     * Gives the owning resource one final check over the exact Schema Version selected for it.
     * Resources that choose latest published Schemas directly have no extra invariant here.
     */
    fun validateSchemaVersionAssignment(resourceId: UUID, schemaVersionId: UUID) = Unit

    /**
     * Whether field values may currently be edited given the resource's lifecycle state. The owning
     * service decides this rule (for Exchanges: only while INITIATED / Draft in the first release).
     */
    fun valuesEditable(resourceId: UUID): Boolean
}
