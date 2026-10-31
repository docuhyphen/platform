package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.fields.*

interface FieldBindingPolicy
{
    fun decide(request: FieldBindingAccess): FieldBindingDecision
}

abstract class AudienceFieldBindingPolicy : FieldBindingPolicy
{
    protected abstract fun isExternalCaller(resource: FieldsResourceRef, access: FieldsAccessContext): Boolean

    override fun decide(request: FieldBindingAccess): FieldBindingDecision
    {
        if (request.binding.visibility != FieldDataClassification.PUBLIC &&
            isExternalCaller(request.resource, request.access)
        )
        {
            return FieldBindingDecision.Deny(FieldBindingDenial.OUT_OF_AUDIENCE)
        }
        if (request.operation == FieldValueOperation.WRITE && request.binding.isReadOnly)
            return FieldBindingDecision.Deny(FieldBindingDenial.READ_ONLY)
        return FieldBindingDecision.Allow
    }
}

class FieldBindingGate(
    private val policy: FieldBindingPolicy,
    private val resource: FieldsResourceRef,
    private val access: FieldsAccessContext,
    private val valueSet: FieldValueSetRef,
)
{
    fun decide(binding: SchemaFieldBinding, operation: FieldValueOperation): FieldBindingDecision =
        policy.decide(FieldBindingAccess(resource, access, valueSet, binding, operation))

    fun permits(binding: SchemaFieldBinding, operation: FieldValueOperation): Boolean =
        decide(binding, operation) is FieldBindingDecision.Allow
}
