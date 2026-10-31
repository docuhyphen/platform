package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest

object InformationRequestResponsePatchRequestMapper
{
    fun hasConflictingNarrativeOperation(request: InformationRequestResponsePatchRequest): Boolean =
        request.clearNarrative && request.narrative != null

    fun toDomain(request: InformationRequestResponsePatchRequest): InformationRequestResponsePatch =
        InformationRequestResponsePatch(
            requirementId = request.requirementId,
            disposition = request.disposition,
            narrative = when
            {
                request.clearNarrative -> ResponseNarrativePatch.Clear
                request.narrative != null -> ResponseNarrativePatch.Set(request.narrative)
                else -> ResponseNarrativePatch.Unchanged
            },
            fieldValues = request.fieldValues?.let {
                ResponseFieldValuesPatch(
                    entries = it.values,
                    precondition = it.etag
                        ?.let(FieldsPrecondition::ExpectedRevision)
                        ?: FieldsPrecondition.Absent,
                )
            },
        )
}
