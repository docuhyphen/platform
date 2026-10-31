package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import com.docuhyphen.app.api.service.fields.BusinessFieldsSubscriptionGuard
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService

internal data class ExactVersionAssignmentFixture(
    val service: SchemaAssignmentService,
    val savedAssignments: MutableList<SchemaAssignment>,
    val savedValueSets: MutableList<FieldValueSet>,
    val schemaVersionRepository: SchemaVersionRepository,
    val subscriptionGuard: BusinessFieldsSubscriptionGuard,
)
