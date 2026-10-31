package com.docuhyphen.app.api.model.informationrequest.evidence

enum class InformationRequestEvidenceSurface
{
    AUTHENTICATED,
    NO_AUTH,
}

data class InformationRequestEvidenceUploadLimits(
    val maximumFileBytes: Long,
    val maximumNoAuthFileBytes: Long,
    val maximumRequestFiles: Long,
    val maximumRequestBytes: Long,
    val maximumPartyFiles: Long,
    val maximumPartyBytes: Long,
) {
    fun fileLimit(surface: InformationRequestEvidenceSurface): Long =
        when (surface) {
            InformationRequestEvidenceSurface.AUTHENTICATED -> maximumFileBytes
            InformationRequestEvidenceSurface.NO_AUTH -> minOf(maximumFileBytes, maximumNoAuthFileBytes)
        }

    fun requestBodyCeiling(surface: InformationRequestEvidenceSurface): Long =
        fileLimit(surface) + MULTIPART_ENVELOPE_BYTES

    companion object {
        const val MULTIPART_ENVELOPE_BYTES = 65_536L
    }
}

data class InformationRequestEvidenceStoredUsage(
    val files: Long,
    val bytes: Long,
)
