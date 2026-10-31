package com.docuhyphen.app.api.model.entity

enum class InformationRequestOwnerType
{
    ORGANIZATION,
    USER,
}

enum class InformationRequestTransitionActorKind
{
    USER,
    PARTICIPANT,
    PRINCIPAL_GROUP,
    ORGANIZATION,
    APPLICATION,
    SERVICE_ACCOUNT,
    PUBLIC_LINK,
    ACCESS_SESSION,
}

enum class InformationRequestEvidenceSourceKind
{
    DOCUMENT_VERSION,
}

enum class InformationRequestEvidenceCollectionState
{
    ACTIVE,
    WITHDRAWN,
    REMOVED,
}

enum class InformationRequestEvidenceAssessmentKind
{
    CONTENT_INSPECTION,
    MALWARE_SCAN,
}
