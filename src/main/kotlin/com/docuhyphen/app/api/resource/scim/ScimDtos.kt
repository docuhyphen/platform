package com.docuhyphen.app.api.resource.scim

import kotlinx.serialization.Serializable

@Serializable
data class ScimName(
    val givenName: String? = null,
    val familyName: String? = null,
    val formatted: String? = null,
)

@Serializable
data class ScimEmail(
    val value: String,
    val type: String? = "work",
    val primary: Boolean = true,
)

@Serializable
data class ScimUser(
    val schemas: List<String> = listOf("urn:ietf:params:scim:schemas:core:2.0:User"),
    val id: String? = null,
    val externalId: String? = null,
    val userName: String,
    val name: ScimName? = null,
    val emails: List<ScimEmail> = emptyList(),
    val active: Boolean = true,
    val meta: ScimMeta? = null,
)

@Serializable
data class ScimMeta(
    val resourceType: String = "User",
    val created: String? = null,
    val lastModified: String? = null,
    val location: String? = null,
)

@Serializable
data class ScimListResponse<T>(
    val schemas: List<String> = listOf("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
    val totalResults: Int,
    val startIndex: Int = 1,
    val itemsPerPage: Int,
    @kotlinx.serialization.SerialName("Resources") val resources: List<T>,
)

@Serializable
data class ScimError(
    val schemas: List<String> = listOf("urn:ietf:params:scim:api:messages:2.0:Error"),
    val status: String,
    val detail: String? = null,
    val scimType: String? = null,
)

@Serializable
data class ScimPatchOperation(
    val op: String,
    val path: String? = null,
    val value: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class ScimPatchRequest(
    val schemas: List<String> = listOf("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
    val Operations: List<ScimPatchOperation>,
)
