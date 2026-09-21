package com.docuhyphen.app.api.model.informationrequest

import java.util.UUID

sealed interface InformationRequestEvidenceSource

data class InformationRequestDocumentVersionEvidenceSource(
    val documentVersionId: UUID,
) : InformationRequestEvidenceSource

data class InformationRequestExternalEvidenceSource(
    val referenceType: String,
    val referenceValue: String,
) : InformationRequestEvidenceSource
{
    init
    {
        require(referenceType.isNotBlank()) { "Evidence reference type must not be blank" }
        require(referenceValue.isNotBlank()) { "Evidence reference value must not be blank" }
    }
}
