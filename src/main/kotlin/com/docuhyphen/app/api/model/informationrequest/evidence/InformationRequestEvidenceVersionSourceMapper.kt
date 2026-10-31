package com.docuhyphen.app.api.model.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceSourceKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion

object InformationRequestEvidenceVersionSourceMapper
{
    fun read(version: InformationRequestEvidenceVersion): InformationRequestDocumentVersionEvidenceSource =
        InformationRequestDocumentVersionEvidenceSource(version.documentVersionId)

    fun write(version: InformationRequestEvidenceVersion, source: InformationRequestDocumentVersionEvidenceSource) {
        version.sourceKind = InformationRequestEvidenceSourceKind.DOCUMENT_VERSION
        version.documentVersionId = source.documentVersionId
    }
}
