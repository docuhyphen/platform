package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.service.command.RevisionETag

/**
 * The entity tag that names one exact state of a [FieldValueSet].
 *
 * It is a strong validator: it is derived only from the set's identity and its persisted revision,
 * both of which change exactly when the stored answers change, so two representations carrying the
 * same tag hold the same answers. Neither a timestamp nor a serialized projection is used, because
 * two changes can share a clock tick and a projection can differ without any answer differing.
 *
 * Naming the set as well as its revision means a tag taken from one set can never be spent against
 * another, which matters as soon as an assignment holds a repetition beside the answers it gives as
 * itself.
 */
object FieldValueSetETag
{
    /** The validator for the state [valueSet] currently stands in, quoted as a strong tag. */
    fun of(valueSet: FieldValueSet): String = RevisionETag.of(valueSet.id, valueSet.revision)
}
