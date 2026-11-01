package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.ResourceContextResolution
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.auth.authz.ResourceReference
import com.docuhyphen.app.api.model.entity.ResourceType
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

@ApplicationScoped
class ResourceAuthorizationContextRegistry
{
    @Inject
    @Any
    private lateinit var providers: Instance<ResourceAuthorizationContextProvider>

    private lateinit var index: Map<ResourceKind, ResourceAuthorizationContextProvider>

    @PostConstruct
    fun init()
    {
        val map = mutableMapOf<ResourceKind, ResourceAuthorizationContextProvider>()
        for (provider in providers)
        {
            val kind = provider.supportedKind
            check(kind !in map) {
                "Duplicate ResourceAuthorizationContextProvider for $kind: " +
                        "${map[kind]!!::class.qualifiedName} vs ${provider::class.qualifiedName}"
            }
            map[kind] = provider
        }
        index = map
    }

    fun resolve(ref: ResourceRef): ResourceAuthorizationContext?
    {
        val kind = ref.type.toResourceKind() ?: return null
        return index[kind]?.resolve(ref.id)
    }

    fun resolve(ref: ResourceReference): ResourceAuthorizationContext? =
        index[ref.kind]?.resolve(ref.id)

    fun resolution(ref: ResourceRef): ResourceContextResolution
    {
        val kind = ref.type.toResourceKind() ?: return ResourceContextResolution.NotGoverned
        val provider = index[kind] ?: return ResourceContextResolution.Unresolved
        val context = provider.resolve(ref.id) ?: return ResourceContextResolution.Unresolved
        return ResourceContextResolution.Resolved(context)
    }

    fun kindOf(ref: ResourceRef): ResourceKind? = ref.type.toResourceKind()

    fun actionsWhileArchived(ref: ResourceRef): Set<Action> =
        ref.type.toResourceKind()?.let { index[it]?.actionsWhileArchived }.orEmpty()

    companion object
    {
        fun ResourceType.toResourceKind(): ResourceKind? = when (this)
        {
            ResourceType.EXCHANGE -> ResourceKind.EXCHANGE
            ResourceType.DOCUMENT -> ResourceKind.DOCUMENT
            ResourceType.PRINCIPAL_GROUP -> ResourceKind.PRINCIPAL_GROUP
            ResourceType.DOC_LIBRARY -> ResourceKind.DOCUMENT_LIBRARY_ENTRY
            ResourceType.BLUEPRINT -> ResourceKind.BLUEPRINT
            ResourceType.WORKFLOW_DEFINITION -> ResourceKind.WORKFLOW_DEFINITION
            ResourceType.SEQUENCE -> ResourceKind.SEQUENCE_DEFINITION
            ResourceType.VARIABLE -> ResourceKind.VARIABLE_DEFINITION
            ResourceType.COMMUNICATION -> ResourceKind.COMMUNICATION
            ResourceType.APPLICATION -> null
            ResourceType.WORKFLOW_WEBHOOK_ENDPOINT -> null
            ResourceType.ORGANIZATION -> ResourceKind.ORGANIZATION
            ResourceType.INFORMATION_REQUEST -> ResourceKind.INFORMATION_REQUEST
            ResourceType.INFORMATION_REQUEST_PARTY -> ResourceKind.INFORMATION_REQUEST_PARTY
            ResourceType.INFORMATION_REQUEST_DELEGATED_AUTHORITY -> ResourceKind.INFORMATION_REQUEST_DELEGATED_AUTHORITY
            ResourceType.INFORMATION_REQUEST_REQUIREMENT -> ResourceKind.INFORMATION_REQUEST_REQUIREMENT
            ResourceType.INFORMATION_REQUEST_ACCESS_LINK -> ResourceKind.INFORMATION_REQUEST_ACCESS_LINK
            ResourceType.INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK -> ResourceKind.INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK
            ResourceType.INFORMATION_REQUEST_EVIDENCE_ARTIFACT -> ResourceKind.INFORMATION_REQUEST_EVIDENCE_ARTIFACT
            ResourceType.INFORMATION_REQUEST_SUBMISSION_PACKAGE -> ResourceKind.INFORMATION_REQUEST_SUBMISSION_PACKAGE
            ResourceType.INFORMATION_REQUEST_SUBMISSION_ATTESTATION -> ResourceKind.INFORMATION_REQUEST_SUBMISSION_ATTESTATION
            ResourceType.INFORMATION_REQUEST_AMENDMENT -> ResourceKind.INFORMATION_REQUEST_AMENDMENT
            ResourceType.INFORMATION_REQUEST_REVIEW -> ResourceKind.INFORMATION_REQUEST_REVIEW
            ResourceType.INFORMATION_REQUEST_REVIEW_ASSIGNMENT -> ResourceKind.INFORMATION_REQUEST_REVIEW_ASSIGNMENT
            ResourceType.INFORMATION_REQUEST_REVIEW_FINDING -> ResourceKind.INFORMATION_REQUEST_REVIEW_FINDING
            ResourceType.INFORMATION_REQUEST_REVIEW_COMMENT -> ResourceKind.INFORMATION_REQUEST_REVIEW_COMMENT
            ResourceType.INFORMATION_REQUEST_ACCEPTED_FACT -> ResourceKind.INFORMATION_REQUEST_ACCEPTED_FACT
            ResourceType.INFORMATION_REQUEST_FACT_RECERTIFICATION -> ResourceKind.INFORMATION_REQUEST_FACT_RECERTIFICATION
            ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE -> ResourceKind.INFORMATION_REQUEST_EXTERNAL_SOURCE
            ResourceType.INFORMATION_REQUEST_BUSINESS_DECISION -> ResourceKind.INFORMATION_REQUEST_BUSINESS_DECISION
            ResourceType.INFORMATION_REQUEST_RECORD_EXPORT -> ResourceKind.INFORMATION_REQUEST_RECORD_EXPORT
            ResourceType.INFORMATION_REQUEST_PRIVACY_REQUEST -> ResourceKind.INFORMATION_REQUEST_PRIVACY_REQUEST
            ResourceType.INFORMATION_REQUEST_SUBJECT_RESTRICTION -> ResourceKind.INFORMATION_REQUEST_SUBJECT_RESTRICTION
            ResourceType.RECORD_PRESERVATION_HOLD -> ResourceKind.RECORD_PRESERVATION_HOLD
            ResourceType.RECORD_RETENTION_SCHEDULE -> ResourceKind.RECORD_RETENTION_SCHEDULE
            ResourceType.INFORMATION_REQUEST_CLOCK_POLICY -> ResourceKind.INFORMATION_REQUEST_CLOCK_POLICY
            ResourceType.INFORMATION_REQUEST_TEMPLATE -> ResourceKind.INFORMATION_REQUEST_TEMPLATE
        }
    }
}
