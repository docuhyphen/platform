package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService

internal data class StableFieldBindingFixture(
    val service: SchemaDefinitionService,
    val savedBindings: MutableList<SchemaFieldBinding>,
)
