package com.docuhyphen.app.api.resource.model

import kotlinx.serialization.Serializable

@Serializable
data class OrganizationIdentityDomainCreateRequest(
    val domain: String,
)

@Serializable
data class OrganizationIdentityDomainResponse(
    val id: String,
    val organizationId: String,
    val domain: String,
    val status: String,
    val dnsRecordName: String,
    val dnsRecordValue: String,
    val createdDate: String,
    val verifiedDate: String? = null,
)
