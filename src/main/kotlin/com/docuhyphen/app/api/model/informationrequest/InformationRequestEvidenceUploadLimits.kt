package com.docuhyphen.app.api.model.informationrequest

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
)

data class InformationRequestEvidenceStoredUsage(
    val files: Long,
    val bytes: Long,
)
