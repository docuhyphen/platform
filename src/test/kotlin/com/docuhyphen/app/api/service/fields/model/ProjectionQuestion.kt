package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding

internal data class ProjectionQuestion(
    val binding: SchemaFieldBinding,
    val contract: FieldContract,
    val definition: FieldDefinition,
)
