package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import java.util.*

internal data class DefaultAndSparseValueFixture(
    val service: SchemaAssignmentService,
    val stored: MutableMap<UUID, FieldValue>,
    val saved: MutableList<FieldValue>,
    val updated: MutableList<FieldValue>,
    val valueRepository: FieldValueRepository,
)
