package com.dochyphen.app.api.resource.model

import com.dochyphen.app.api.model.entity.*
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
    var idType: PersonIDType? = null
)

@Serializable
data class PersonRegistrationResponse(var person: Person)

@Serializable
data class CompanyRegistrationRequest(
    var name: String? = null,
    var registrationNumber: String? = null
)

@Serializable
data class CompanyRegistrationResponse(var company: Company)

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
)

@Serializable
data class UpdateSharingSessionRequest(
    var initialShareMessage: String? = null,
    var description: String? = null,
    var sessionName: String? = null,
    var allowDocumentAddition: Boolean = false,
    var allowDocumentDeletion: Boolean = false,
    var allowDocumentDownload: Boolean = false,
    var allowDocumentUpdate: Boolean = false,
    var allowDocumentUpload: Boolean = false,
    var status: SharingSessionStatus? = null,
    var rejectionReason: String? = null
)

@Serializable
class AddSharingSessionDocumentRequest(
    val title: String?,
    val documentType: DocumentType? = null,
    val restrictedType: DocumentType? = null,
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
    var role: SharingSessionParticipantRole = SharingSessionParticipantRole.VIEWER
}

@Serializable
class UpdateShareSessionDocumentRequest
{
    var title: String? = null
    var restrictedType: DocumentType? = null
    var type: DocumentType? = null
}

@Serializable
data class CommentRequest(
    val commentText: String,
    val commentedBy: String
)