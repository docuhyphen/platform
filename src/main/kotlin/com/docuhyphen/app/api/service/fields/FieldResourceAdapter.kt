package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import java.util.*

interface FieldResourceAdapter
{
    val resourceType: String

    val bindingPolicy: FieldBindingPolicy

    fun exists(resourceId: UUID): Boolean

    fun ownerScope(resourceId: UUID): ScopeReference?

    fun subscriptionContext(resourceId: UUID): SubscriptionContext?

    fun mutationEntitlementFrozen(resourceId: UUID): Boolean = false

    fun authorizeViewFields(resourceId: UUID, principal: PrincipalRef, context: AuthorizationContext)

    fun authorizeManageFields(resourceId: UUID, principal: PrincipalRef, context: AuthorizationContext)

    fun authorizeManageSchema(resourceId: UUID, principal: PrincipalRef, context: AuthorizationContext)

    fun schemaAssignmentMutable(resourceId: UUID): Boolean = valuesEditable(resourceId)

    fun validateSchemaVersionAssignment(resourceId: UUID, schemaVersionId: UUID) = Unit

    fun valuesEditable(resourceId: UUID): Boolean
}
