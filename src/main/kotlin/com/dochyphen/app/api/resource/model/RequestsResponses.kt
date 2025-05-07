package com.dochyphen.app.api.resource.model

import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.AppUserRole
import com.dochyphen.app.api.model.entity.DocumentType
import com.dochyphen.app.api.model.entity.Person
import com.dochyphen.app.api.model.entity.SharingSessionStatus
import kotlinx.serialization.Serializable

@Serializable
data class ResponseError(var errorMessage: String? = "")

@Serializable
data class SignInRequest(var email: String? = null, var password: String? = null)

@Serializable
data class SignInResponse(var message: String)

@Serializable
data class SignInCompletionRequest(
    var otp: String? = null,
    var email: String? = null
)

@Serializable
data class SignInCompletionResponse(var token: String? = null)

@Serializable
data class SignOutRequest(var appUser: AppUser)

@Serializable
data class SignUpCompletionRequest(
    var email: String? = null,
    var otp: String? = null,
    val password: String? = null,
    val confirmationPassword: String? = null
)

@Serializable
data class SignUpCompletionResponse(
    var message: String? = null
)

@Serializable
data class PasswordResetCompletionRequest(
    var email: String? = null,
    var otp: String? = null,
    val password: String? = null,
    val confirmationPassword: String? = null
)

@Serializable
data class SignUpInitiateRequest(var email: String? = null)

@Serializable
data class SignUpInitiateResponse(var message: String? = null)

@Serializable
data class SignUpRegenerationRequest(var email: String? = null)

@Serializable
data class SignUpRegenerationResponse(var message: String? = null)

@Serializable
data class PasswordResetInitiationRequest(var email: String? = null)

@Serializable
data class PersonRegistrationRequest(
    var firstName: String? = null,
    var lastName: String? = null,
    var idNumber: String? = null,
    var idType: String? = null
)

@Serializable
data class PersonRegistrationResponse(var person: Person)

@Serializable
data class OrganizationRegistrationRequest(
    var name: String? = null,
    var registrationNumber: String? = null,
    var phoneNumber: String? = null,
    var email: String? = null
)

@Serializable
data class AddOrganizationGroupMemberRequest(
    var appUserId: String? = null,
    var allowSessionAccept: Boolean = false,
    var allowSessionReject: Boolean = false,
    var allowSessionEdit: Boolean = false,
    var allowSessionDelete: Boolean = false,
    var allowSessionEnd: Boolean = false,
    var allowDocumentAddition: Boolean = false,
    var allowDocumentDeletion: Boolean = false,
    var allowDocumentDownload: Boolean = false,
    var allowDocumentUpdate: Boolean = false,
    var allowDocumentUpload: Boolean = false
)

@Serializable
data class AddOrganizationGroupRequest(
    var name: String? = null,
    var members: List<AddOrganizationGroupMemberRequest>? = mutableListOf(),
)

@Serializable
data class UpdateOrganizationGroupRequest(
    var name: String? = null,
    var isActive: Boolean = false,
    var members: List<AddOrganizationGroupMemberRequest>? = mutableListOf(),
)

@Serializable
data class UpdateOrganizationRequest(
    var name: String? = null,
    var registrationNumber: String? = null
)

@Serializable
data class AddOrganizationAppUserPersonRequest(
    var firstName: String? = null,
    var lastName: String? = null,
)

@Serializable
data class AddOrganizationAppUserRequest(
    var role: AppUserRole? = null,
    var email: String? = null,
    var person: AddOrganizationAppUserPersonRequest?,
)

@Serializable
data class UpdateOrganizationAppUserRequest(
    var role: String? = null,
    var isActive: Boolean? = null,
    var email: String? = null,
    var person: AddOrganizationAppUserPersonRequest?,
)

@Serializable
data class SharingSessionInitiationRequest(
    var initialShareMessage: String? = null,
    var description: String? = null,
    var recipientEmail: String? = null,
    var sessionName: String? = null,
    var sessionDocuments: List<SharingSessionRequestDocumentRequest>? = null,
    var requestRecipientSignIn: Boolean = false,
    var allowDocumentAddition: Boolean = false,
    var allowDocumentDeletion: Boolean = false,
    var allowDocumentDownload: Boolean = false,
    var allowDocumentUpdate: Boolean = false,
    var allowDocumentUpload: Boolean = false,
    var participants: List<SharingSessionParticipantRequest>? = null,
    var status: SharingSessionStatus? = null,
    var rejectionReason: String? = null
    //ToDo: add accepted by, rejected by, ended by
)

@Serializable
data class UpdateSharingSessionRequest(
    var initialShareMessage: String? = null,
    var description: String? = null,
    var sessionName: String? = null,
    var requireRecipientSignIn: Boolean? = null,
    var allowDocumentAddition: Boolean? = null,
    var allowDocumentDeletion: Boolean? = null,
    var allowDocumentDownload: Boolean? = null,
    var allowDocumentUpload: Boolean? = null,
    var allowDocumentUpdate: Boolean? = null,
    var status: SharingSessionStatus? = null,
    var rejectionReason: String? = null
)

@Serializable
data class UpdateNoAuthSharingSession(
    var status: SharingSessionStatus? = null,
    var otp: String? = null,
    var rejectReason: String? = null
)


@Serializable
class AddSharingSessionDocumentRequest(
    val title: String?,
    val documentType: DocumentType? = null,
    val restrictedType: DocumentType? = null,
    val restrictType: Boolean? = null,
)

@Serializable
class DownloadSharingSessionDocumentRequest(
    val documentId: String,
    val sessionId: String,
)

@Serializable
class SharingSessionRequestDocumentRequest
{
    var title: String = ""
    var restrictedType: DocumentType? = null
    var type: DocumentType? = null
    var restrictType: Boolean = false
}

@Serializable
class SharingSessionParticipantRequest
{
    var id: String = ""
}

@Serializable
class UpdateShareSessionDocumentRequest
{
    var title: String? = null
    var restrictedType: DocumentType? = null
    var restrictType: Boolean? = null
    var type: DocumentType? = null
}

@Serializable
data class CommentRequest(
    val commentText: String,
    val commentedBy: String
)

@Serializable
data class DownloadDocumentsZipRequest(
    val documentIds: List<String>
)

@Serializable
data class InitiatePhoneNumberRequest(
    val phoneNumber: String
)

@Serializable
data class CompletePhoneNumberRequest(
    val phoneNumber: String,
    val verificationCode: String
)

@Serializable
data class InitiateEmailRequest(
    val email: String
)

@Serializable
data class CompleteEmailRequest(
    val email: String,
    val verificationCode: String
)