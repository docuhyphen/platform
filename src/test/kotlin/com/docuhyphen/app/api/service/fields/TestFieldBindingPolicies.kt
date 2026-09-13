package com.docuhyphen.app.api.service.fields

/**
 * Per-binding policies for tests about the Fields engine rather than about any one resource domain.
 * They apply the shared audience and configuration rules and are simply told whether the caller
 * stands outside the resource, which is the one thing a real resource domain works out for itself.
 */
internal class AudienceBindingPolicy(private val external: Boolean) : AudienceFieldBindingPolicy()
{
    override fun isExternalCaller(resource: FieldsResourceRef, access: FieldsAccessContext) = external
}

/** The policy governing a caller who belongs to the organization that owns the resource. */
internal object InternalCallerBindingPolicy : FieldBindingPolicy by AudienceBindingPolicy(external = false)
