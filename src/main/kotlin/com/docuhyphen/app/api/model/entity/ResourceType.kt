package com.docuhyphen.app.api.model.entity

/**
 * Resource types recognized by the authorization and resource-context registry.
 * Each type that can hold a Share must also have a provider registered in
 * ResourceAuthorizationContextRegistry.
 */
enum class ResourceType
{
    EXCHANGE,
    DOCUMENT,
    PRINCIPAL_GROUP,
    DOC_LIBRARY,
    BLUEPRINT,
    WORKFLOW_DEFINITION,
    SEQUENCE,
    VARIABLE,
    COMMUNICATION,
    APPLICATION,
    WORKFLOW_WEBHOOK_ENDPOINT,
    ORGANIZATION,

    /** A runtime Information Request aggregate. */
    INFORMATION_REQUEST,

    /** One request-scoped party assignment recorded as a command result. */
    INFORMATION_REQUEST_PARTY,

    /** One delegated-authority grant recorded as a command result. */
    INFORMATION_REQUEST_DELEGATED_AUTHORITY,

    /**
     * One runtime Requirement occurrence inside an Information Request. It is a resource of its
     * own because assignment, response mode, confidentiality, and correction scope are decided
     * per occurrence rather than for the whole request.
     */
    INFORMATION_REQUEST_REQUIREMENT,

    /** One bootstrap-mode ShareLink issuance recorded as a command result. */
    INFORMATION_REQUEST_ACCESS_LINK,

    /** One Participant-to-App-User registration upgrade recorded as a command result. */
    INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK,
}
