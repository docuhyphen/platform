package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.service.command.RevisionETag

object FieldValueSetETag
{
    fun of(valueSet: FieldValueSet): String = RevisionETag.of(valueSet.id, valueSet.revision)
}
