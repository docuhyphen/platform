package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService

internal data class AudienceAuthorizationFixture(
    val service: SchemaAssignmentService,
    val valueRepository: FieldValueRepository,
    val assignmentRepository: SchemaAssignmentRepository,
    val access: FieldsAccessContext,
)
