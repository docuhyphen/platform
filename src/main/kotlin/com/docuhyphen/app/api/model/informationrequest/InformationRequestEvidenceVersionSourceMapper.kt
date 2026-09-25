package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceSourceKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion

object InformationRequestEvidenceVersionSourceMapper
{
    fun read(version: InformationRequestEvidenceVersion): InformationRequestEvidenceSource =
        when (version.sourceKind)
        {
            InformationRequestEvidenceSourceKind.DOCUMENT_VERSION ->
                InformationRequestDocumentVersionEvidenceSource(
                    requireNotNull(version.documentVersionId) {
                        "A file-backed evidence version names an exact Document Version"
                    },
                )

            InformationRequestEvidenceSourceKind.EXTERNAL_REFERENCE ->
                InformationRequestExternalEvidenceSource(
                    requireNotNull(version.externalReferenceType) {
                        "An external evidence version names a reference type"
                    },
                    requireNotNull(version.externalReferenceValue) {
                        "An external evidence version names a reference value"
                    },
                )
        }

    fun write(version: InformationRequestEvidenceVersion, source: InformationRequestEvidenceSource)
    {
        when (source)
        {
            is InformationRequestDocumentVersionEvidenceSource ->
            {
                version.sourceKind = InformationRequestEvidenceSourceKind.DOCUMENT_VERSION
                version.documentVersionId = source.documentVersionId
                version.externalReferenceType = null
                version.externalReferenceValue = null
            }

            is InformationRequestExternalEvidenceSource ->
            {
                version.sourceKind = InformationRequestEvidenceSourceKind.EXTERNAL_REFERENCE
                version.documentVersionId = null
                version.externalReferenceType = source.referenceType
                version.externalReferenceValue = source.referenceValue
            }
        }
    }
}
