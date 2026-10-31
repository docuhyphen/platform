package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.fields.FieldsResourceRef

internal class AudienceBindingPolicy(private val external: Boolean) : AudienceFieldBindingPolicy()
{
    override fun isExternalCaller(resource: FieldsResourceRef, access: FieldsAccessContext) = external
}

internal object InternalCallerBindingPolicy : FieldBindingPolicy by AudienceBindingPolicy(external = false)
