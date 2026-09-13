package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponsePatch
import com.docuhyphen.app.api.service.informationrequest.ResponseFieldValuesPatch
import com.docuhyphen.app.api.service.informationrequest.ResponseNarrativePatch
import com.docuhyphen.app.api.service.fields.FieldsPrecondition

/**
 * Turns the wire shape of one response patch into the domain patch the response draft service
 * expects, once the resource has already confirmed [hasConflictingNarrativeOperation] is false.
 */
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
