package com.docuhyphen.app.api.model.entity

/**
 * Runtime Information Request ownership. The persisted values match the audit and event owner
 * vocabulary so a request can file history under a single exact owner.
 */
enum class InformationRequestOwnerType
{
    ORGANIZATION,
    USER,
}

/**
 * The actor recorded in transition history. Most actors are principal kinds; an access session is
 * a verified request-scoped session rather than a durable platform principal.
 */
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

/**
 * How one recorded evidence version names its content. A file-backed version names an exact
 * Document Version; an external version names a typed reference held outside this platform.
 */
enum class InformationRequestEvidenceSourceKind
{
    DOCUMENT_VERSION,
    EXTERNAL_REFERENCE,
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
