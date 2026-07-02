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
}
