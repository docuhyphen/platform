package com.docuhyphen.app.api.model.informationrequest.evidence

data class InformationRequestEvidenceDocumentLimits(
    val maximumDecodedBytes: Long = MAXIMUM_DECODED_BYTES,
    val maximumCrossReferenceEntries: Long = MAXIMUM_CROSS_REFERENCE_ENTRIES,
    val maximumCrossReferenceSections: Long = MAXIMUM_CROSS_REFERENCE_SECTIONS,
    val maximumStreamObjects: Long = MAXIMUM_STREAM_OBJECTS,
) {
    companion object {
        const val MAXIMUM_DECODED_BYTES = 16L * 1024 * 1024
        const val MAXIMUM_CROSS_REFERENCE_ENTRIES = 500_000L
        const val MAXIMUM_CROSS_REFERENCE_SECTIONS = 10_000L
        const val MAXIMUM_STREAM_OBJECTS = 1_000_000L
    }
}
