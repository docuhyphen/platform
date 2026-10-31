package com.docuhyphen.app.api.service.fields.model

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.service.fields.ExchangeFieldQueryService
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import java.util.*

internal data class RootValueSetFixture(
    val service: SchemaAssignmentService,
    val queryService: ExchangeFieldQueryService,
    val storedByValueSet: MutableMap<UUID, MutableMap<UUID, FieldValue>>,
    val savedSets: MutableList<FieldValueSet>,
    val deletedSets: MutableList<FieldValueSet>,
    val deletedValues: MutableList<FieldValue>,
    val valueSetRepository: FieldValueSetRepository,
)
