package com.dochyphen.app.api.model.entity

enum class AppFeatureType
{
    INITIATE_SHARING_SESSION,
    ACCEPT_SHARING_SESSION,
    REJECT_SHARING_SESSION,
    END_SHARING_SESSION,
    ADD_SHARING_SESSION_DOCUMENT,
    EDIT_SHARING_SESSION_DOCUMENT,
    MANAGE_SHARING_SESSION_PERMISSIONS,
    DELETE_SHARING_SESSION_PERMISSIONS,

    DOWNLOAD_DOCUMENT,
    UPLOAD_DOCUMENT,
    DELETE_DOCUMENT,
}

class AppFeature
{
    var id: String = ""
    var name: String = ""
    var description: String? = null
    var isActive: Boolean = true
    var createdDate: String = ""
    var updatedDate: String? = null

}