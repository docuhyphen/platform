package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding

/** One caller's question about one binding of the Schema assigned to a resource. */
data class FieldBindingAccess(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val valueSet: FieldValueSetRef,
    val binding: SchemaFieldBinding,
    val operation: FieldValueOperation,
)

/** Why one binding is closed to one caller, in terms the refusal message is built from. */
enum class FieldBindingDenial
{
    /** The binding is outside the audience this caller may be shown. */
    OUT_OF_AUDIENCE,

    /** The binding's value is maintained by configuration rather than supplied by a caller. */
    READ_ONLY,
}

sealed interface FieldBindingDecision
{
    data object Allow : FieldBindingDecision

    data class Deny(val reason: FieldBindingDenial) : FieldBindingDecision
}

/**
 * The port through which the Fields engine asks the owning resource domain what one caller may do
 * with one binding of an assigned Schema.
 *
 * Reads and writes ask the same policy, so the bindings a caller can be shown and the bindings a
 * caller can address are decided by one rule rather than by two that can drift apart. A resource
 * domain whose access narrows over the life of its resource, for example to the items a caller was
 * invited to correct, expresses that narrowing here instead of in the Fields engine.
 */
interface FieldBindingPolicy
{
    fun decide(request: FieldBindingAccess): FieldBindingDecision
}

/**
 * The per-binding rules every Fields resource shares: a caller outside the resource's own audience
 * is limited to bindings classified [FieldDataClassification.PUBLIC], and a binding the Schema
 * marks read-only is never written by a caller. A resource domain subclasses this to say who counts
 * as an outside caller and to add whatever narrower rules its own lifecycle requires.
 */
abstract class AudienceFieldBindingPolicy : FieldBindingPolicy
{
    /**
     * Whether the caller reaches the resource from outside the organization or ownership that holds
     * it, for example as a recipient rather than as a member of the owning organization.
     */
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

/**
 * One resource's policy bound to one caller and one set of answers, so the projection and the write
 * path of a single command ask the same question of the same policy.
 */
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
