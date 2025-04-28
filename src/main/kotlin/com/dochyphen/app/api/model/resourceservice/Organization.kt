package com.dochyphen.app.api.model.resourceservice

data class MemberPermissionsModel(
    val allowSessionAccept: Boolean = false,
    val allowSessionReject: Boolean = false,
    val allowSessionEdit: Boolean = false,
    val allowSessionDelete: Boolean = false,
    val allowSessionEnd: Boolean = false,
    val allowDocumentAddition: Boolean = false,
    val allowDocumentDeletion: Boolean = false,
    val allowDocumentDownload: Boolean = false,
    val allowDocumentUpdate: Boolean = false,
    val allowDocumentUpload: Boolean = false
)

data class OrganizationGroupMemberModel(
    val appUserId: String,
    val permissions: MemberPermissionsModel
)