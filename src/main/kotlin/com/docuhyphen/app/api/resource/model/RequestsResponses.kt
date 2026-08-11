package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ResponseError(
    var errorMessage: String? = "",
    var reasonCode: String? = null,
    var retryAfterSeconds: Long? = null,
)

/**
 * Public "Speak to Sales" enquiry submitted from the marketing site. The [website] field is a
 * honeypot: it is hidden from real users and must stay empty. Any submission that fills it is
 * treated as automated spam and silently discarded.
 */
@Serializable
data class SalesEnquiryRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val workEmail: String? = null,
    val phone: String? = null,
    val organizationName: String? = null,
    val organizationType: String? = null,
    val companySize: String? = null,
    val country: String? = null,
    val message: String? = null,
    val website: String? = null,
)

@Serializable
data class SignInRequest(
    var email: String? = null,
    var password: String? = null
)

@Serializable
data class ResendOtpRequest(
    val email: String,
    val mfaSessionId: String
)

@Serializable
data class ResendOtpResponse(
    var message: String,
    val mfaSessionId: String
)

@Serializable
data class SignInResponse(
    var message: String,
    var mfaSessionId: String? = null,
    var mfaType: String? = null,
    var emailFallbackEnabled: Boolean = false,
)

@Serializable
data class SignInCompletionRequest(
    val email: String?,
    val otp: String?,
    val mfaSessionId: String?
)

@Serializable
data class SignInCompletionResponse(
    val accessToken: String,
    val idToken: String,
)

@Serializable
data class SignInLookupRequest(
    val email: String? = null,
    val orgId: String? = null,
)

@Serializable
data class SignInLookupResponse(
    val authMethod: String,
    val redirectUrl: String? = null,
    val outcome: String? = null,
    val fallbackAuthMethod: String? = null,
    val organizations: List<SignInLookupOrganizationOption> = emptyList(),
    val availableProviders: List<String> = emptyList(),
)

@Serializable
data class SignInLookupOrganizationOption(
    val id: String,
    val name: String,
)

@Serializable
data class TokenRefreshResponse(
    val accessToken: String,
    val idToken: String,
)

@Serializable
data class OAuthLinkConfirmRequest(
    val linkToken: String? = null,
    val password: String? = null,
)

@Serializable
data class OAuthLinkConfirmResponse(
    val accessToken: String,
    val idToken: String,
)

@Serializable
data class IdentityProviderLinkDto(
    val provider: String,
    val externalEmail: String,
    val createdDate: String,
)

@Serializable
data class LinkProviderInitiateRequest(
    val provider: String? = null,
)

@Serializable
data class LinkProviderInitiateResponse(
    val redirectUrl: String,
)

@Serializable
data class ApplicationTokenRequest(
    val apiKey: String? = null,
    val apiSecret: String? = null,
)

@Serializable
data class ApplicationTokenResponse(
    val accessToken: String,
)

@Serializable
data class SetupPasswordRequest(
    val password: String? = null,
    val confirmationPassword: String? = null,
    /** Required only when the account already has a password, so a change proves knowledge of it. */
    val currentPassword: String? = null,
)

@Serializable
data class SignOutRequest(
    var appUser: AppUser
)

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

/** Request body for POST /auth/sign-up/email-confirm,  the opaque-token completion path. */
@Serializable
data class SignUpEmailConfirmRequest(
    val token: String? = null,
    val password: String? = null,
    val confirmationPassword: String? = null
)

/** Response from POST /auth/sign-up/email-confirm. */
@Serializable
data class SignUpEmailConfirmResponse(var message: String? = null)

/** Response from GET /auth/sign-up/email-confirm/{token},  token introspection (no consumption). */
@Serializable
data class SignUpEmailConfirmCheckResponse(var email: String? = null)

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
    var groupRole: PrincipalGroupRoleName = PrincipalGroupRoleName.MEMBER,
)

@Serializable
data class AddOrganizationGroupRequest(
    var name: String? = null,
    var members: List<AddOrganizationGroupMemberRequest>? = mutableListOf(),
    var externallyPublished: Boolean = false,
)

@Serializable
data class UpdateOrganizationGroupRequest(
    var name: String? = null,
    var isActive: Boolean = false,
    var members: List<AddOrganizationGroupMemberRequest>? = mutableListOf(),
    var externallyPublished: Boolean = false,
)

@Serializable
data class UpdateOrganizationRequest(
    var name: String? = null,
    var registrationNumber: String? = null
)

/** POST /admin/roles/app-admins, promote a user to App Admin. */
@Serializable
data class GrantAppAdminRequest(
    var appUserId: String? = null,
)

/** A single App Admin in the GET /admin/roles/app-admins listing. */
@Serializable
data class AppAdminDto(
    val assignmentId: String,
    val appUserId: String?,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val grantedByAppUserId: String? = null,
    val grantedAt: String? = null,
)

/**
 * An app-user search hit, returned by `GET /admin/users/search?q=…`. Used by the
 * App Admins picker since app-admin is a global role and isn't constrained to the caller's org.
 */
@Serializable
data class AppUserSearchResultDto(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class OAuthTokenExchangeRequest(
    val code: String? = null,
)

@Serializable
data class OAuthTokenExchangeResponse(
    val accessToken: String,
    val idToken: String,
    val isNewUser: Boolean,
)

@Serializable
data class AddOrganizationAppUserPersonRequest(
    var firstName: String? = null,
    var lastName: String? = null,
)

@Serializable
data class AddOrganizationAppUserRequest(
    var roles: Set<OrganizationRoleName> = setOf(OrganizationRoleName.ORG_MEMBER),
    var email: String? = null,
    var person: AddOrganizationAppUserPersonRequest?,
)

@Serializable
data class UpdateOrganizationAppUserRequest(
    var rolesToAdd: Set<OrganizationRoleName> = emptySet(),
    var rolesToRemove: Set<OrganizationRoleName> = emptySet(),
    var isActive: Boolean? = null,
    var email: String? = null,
    var person: AddOrganizationAppUserPersonRequest?,
)

@Serializable
data class ExchangeInitiationDto(
    var initialShareMessage: String? = null,
    var description: String? = null,
    var primaryRecipient: ExchangeRecipientSelectionRequest? = null,
    var name: String? = null,
    var exchangeDocuments: List<ExchangeRequestDocumentRequest>? = null,
    var requestRecipientSignIn: Boolean = false,
    var allowDocumentAddition: Boolean = false,
    var allowDocumentDeletion: Boolean = false,
    var allowDocumentDownload: Boolean = false,
    var allowDocumentUpdate: Boolean = false,
    var allowDocumentUpload: Boolean = false,
    var participants: List<ExchangeParticipantRequest> = listOf(),
    var status: ExchangeStatus? = null,
    var rejectionReason: String? = null,
    var recipientRoleName: String? = null,
    var recipientConstraintsJson: String? = null,
    var allowedDownloadFormats: List<String>? = null,
    var variableOverrides: Map<String, String>? = null,
    var schemaDefinitionId: String? = null,
    var fieldValues: List<FieldValueEntry>? = null,
    var schemaAssignmentSource: SchemaAssignmentSource? = null,
    //ToDo: add accepted by, rejected by, ended by
)

@Serializable
sealed interface ExchangeRecipientSelectionRequest

@Serializable
@SerialName("REGISTERED_USER")
data class RegisteredUserRecipientSelectionRequest(
    val appUserId: String,
) : ExchangeRecipientSelectionRequest

@Serializable
@SerialName("EXTERNAL_EMAIL")
data class ExternalEmailRecipientSelectionRequest(
    val email: String,
    val firstName: String,
    val lastName: String,
) : ExchangeRecipientSelectionRequest

@Serializable
@SerialName("INTERNAL_GROUP")
data class InternalGroupRecipientSelectionRequest(
    val groupId: String,
) : ExchangeRecipientSelectionRequest

@Serializable
@SerialName("PERSONAL_GROUP")
data class PersonalGroupRecipientSelectionRequest(
    val groupId: String,
) : ExchangeRecipientSelectionRequest

@Serializable
@SerialName("TRUSTED_GROUP")
data class TrustedGroupRecipientSelectionRequest(
    val organizationId: String,
    val groupId: String,
) : ExchangeRecipientSelectionRequest

@Serializable
@SerialName("TRUSTED_PERSON")
data class TrustedPersonRecipientSelectionRequest(
    val resolutionId: String,
) : ExchangeRecipientSelectionRequest

@Serializable
data class UpdateExchangeRequest(
    var initialShareMessage: String? = null,
    var description: String? = null,
    var name: String? = null,
    var requireRecipientSignIn: Boolean? = null,
    var allowDocumentAddition: Boolean? = null,
    var allowDocumentDeletion: Boolean? = null,
    var allowDocumentDownload: Boolean? = null,
    var allowDocumentUpload: Boolean? = null,
    var allowDocumentUpdate: Boolean? = null,
    var noAuthAccessValidityDays: Int? = null,
    var status: ExchangeStatus? = null,
    var rejectionReason: String? = null,
    var allowedDownloadFormats: List<String>? = null,
)

@Serializable
data class UpdateNoAuthExchange(
    var status: ExchangeStatus? = null,
    var otp: String? = null,
    var rejectReason: String? = null,
    var rejectionReason: String? = null,
)

@Serializable
class AddExchangeDocumentRequest(
    val title: String?,
    val documentType: DocumentType? = null,
    val restrictedType: DocumentType? = null,
    val restrictType: Boolean? = null,
)

@Serializable
class DownloadExchangeDocumentRequest(
    val documentId: String,
    val exchangeId: String,
)

@Serializable
class ExchangeRequestDocumentRequest
{
    var title: String = ""
    var restrictedType: DocumentType? = null
    var type: DocumentType? = null
    var restrictType: Boolean = false
    var required: Boolean = false
    @Serializable(with = UUIDSerializer::class)
    var libraryDocumentId: UUID? = null
}

@Serializable
data class ExchangeParticipantRequest(
    val selection: ExchangeRecipientSelectionRequest,
    val role: ExchangeShareRoleName = ExchangeShareRoleName.PARTICIPANT,
)

@Serializable
data class AddExchangeParticipantRequest(
    val id: String,
)

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
    val isInternal: Boolean = false,
    val pageNumber: Int? = null,
    val documentVersionId: String? = null,
)

@Serializable
data class DownloadDocumentsZipRequest(
    val documentIds: List<String>
)

@Serializable
data class InitiateAddOrUpdatePhoneNumberRequest(
    val phoneNumber: String
)

@Serializable
data class CompleteAddOrUpdatePhoneNumberRequest(
    val phoneNumber: String,
    val verificationCode: String
)

@Serializable
data class InitiateAddOrUpdateEmailRequest(
    val email: String
)

@Serializable
data class CompleteAddOrUpdateEmailRequest(
    val email: String,
    val verificationCode: String
)

@Serializable
data class ConfirmOldEmailForUpdateRequest(
    val verificationCode: String
)

@Serializable
data class AdminApprovalInitiateRequest(
    val action: String,
    val reason: String? = null,
    val expiresMinutes: Long? = null,
)

@Serializable
data class AdminApprovalInitiateResponse(
    val approvalId: String,
    val status: String,
)

@Serializable
data class AdminApprovalApproveResponse(
    val approvalId: String,
    val status: String,
)

@Serializable
data class OrganizationIdpSecretRotateRequest(
    val newClientSecret: String,
)

@Serializable
data class OrganizationIdpSecretActivateRequest(
    val versionId: String,
)

@Serializable
data class OrganizationIdpSecretRetireRequest(
    val recoveryWindowDays: Int = 7,
)

@Serializable
data class OrganizationIdpSecretRotationResponse(
    val secretRef: String,
    val previousVersionId: String? = null,
    val activeVersionId: String,
    val overlapUntilEpochMillis: Long? = null,
)

@Serializable
data class OrganizationIdpSecretStatusResponse(
    val secretRef: String,
    val disabled: Boolean,
    val rotationPhase: String? = null,
    val currentVersionId: String? = null,
    val previousVersionId: String? = null,
    val pendingVersionId: String? = null,
    val overlapUntilEpochMillis: Long? = null,
    val overlapActive: Boolean,
)

@Serializable
data class OrganizationIdpSecretRollbackResponse(
    val secretRef: String,
    val rolledBackToVersionId: String,
    val previousCurrentVersionId: String? = null,
)

@Serializable
data class OrganizationIdpConfigRequest(
    val provider: String,
    val clientId: String? = null,
    val clientSecretRef: String? = null,
    val tenantId: String? = null,
    val scopes: List<String> = emptyList(),
    val isActive: Boolean = true,
    val accessTokenExpiryMinutes: Long? = null,
    val refreshTokenExpiryMinutes: Long? = null,
    val maxSessionDurationHours: Long? = null,
    val idleTimeoutMinutes: Long? = null,
    val oidcIssuer: String? = null,
    val allowedAudiences: List<String> = emptyList(),
    val allowedAlgs: List<String> = emptyList(),
    val requiredClaims: List<String> = emptyList(),
)

@Serializable
data class OrganizationIdpConfigResponse(
    val id: String,
    val organizationId: String,
    val provider: String,
    val clientId: String? = null,
    val clientSecretRef: String? = null,
    val tenantId: String? = null,
    val scopes: List<String> = emptyList(),
    val isActive: Boolean,
    val accessTokenExpiryMinutes: Long? = null,
    val refreshTokenExpiryMinutes: Long? = null,
    val maxSessionDurationHours: Long? = null,
    val idleTimeoutMinutes: Long? = null,
    val oidcIssuer: String? = null,
    val allowedAudiences: List<String> = emptyList(),
    val allowedAlgs: List<String> = emptyList(),
    val requiredClaims: List<String> = emptyList(),
    val createdDate: String,
    val updatedDate: String,
)

@Serializable
data class OrganizationIdpSecretRotationRunResponse(
    val evaluated: Int,
    val rotated: Int,
    val failed: Int,
    val skipped: Int = 0,
)

@Serializable
data class OrganizationIdpSecretRotationPreviewCandidate(
    val configId: String,
    val provider: String,
    val secretRef: String,
    val updatedDate: String,
    val due: Boolean,
    val overdueByDays: Long,
)

@Serializable
data class OrganizationIdpSecretRotationPreviewResponse(
    val intervalDays: Long,
    val evaluated: Int,
    val dueCount: Int,
    val candidates: List<OrganizationIdpSecretRotationPreviewCandidate>,
)

@Serializable
data class OrganizationIdpSecretRotationStatusItemResponse(
    val configId: String,
    val provider: String,
    val secretRef: String,
    val disabled: Boolean,
    val rotationPhase: String? = null,
    val currentVersionId: String? = null,
    val previousVersionId: String? = null,
    val pendingVersionId: String? = null,
    val overlapUntilEpochMillis: Long? = null,
    val overlapActive: Boolean,
    val due: Boolean,
    val overdueByDays: Long,
)

@Serializable
data class OrganizationIdpSecretRotationStatusResponse(
    val intervalDays: Long,
    val evaluated: Int,
    val dueCount: Int,
    val overlapActiveCount: Int,
    val disabledCount: Int,
    val retirePhaseCount: Int,
    val items: List<OrganizationIdpSecretRotationStatusItemResponse>,
)

@Serializable
data class SecurityIncidentResponse(
    val id: String,
    val incidentType: String,
    val severity: String,
    val actorId: String? = null,
    val requestId: String? = null,
    val details: String? = null,
    val createdDate: String,
)

@Serializable
data class OrganizationAuthSessionPolicyResponse(
    val organizationId: String,
    val appUserId: String? = null,
    val accessTokenExpiryMinutes: Long,
    val refreshTokenExpiryMinutes: Long,
    val maxSessionDurationHours: Long,
    val idleTimeoutMinutes: Long,
)

@Serializable
data class OrganizationAuthSessionPolicyEffectiveDto(
    val accessTokenExpiryMinutes: Long,
    val refreshTokenExpiryMinutes: Long,
    val maxSessionDurationHours: Long,
    val idleTimeoutMinutes: Long,
)

@Serializable
data class OrganizationAuthSessionPolicyGuardrailsDto(
    val minAccessTokenExpiryMinutes: Long,
    val maxAccessTokenExpiryMinutes: Long,
    val minRefreshTokenExpiryMinutes: Long,
    val maxRefreshTokenExpiryMinutes: Long,
    val minSessionMaxDurationHours: Long,
    val maxSessionMaxDurationHours: Long,
    val minIdleTimeoutMinutes: Long,
    val maxIdleTimeoutMinutes: Long,
)

@Serializable
data class OrganizationAuthSessionPolicyIdpDto(
    val configId: String,
    val provider: String,
    val isActive: Boolean,
    val accessTokenExpiryMinutes: Long? = null,
    val refreshTokenExpiryMinutes: Long? = null,
    val maxSessionDurationHours: Long? = null,
    val idleTimeoutMinutes: Long? = null,
)

@Serializable
data class OrganizationAuthSessionPolicySettingsResponse(
    val organizationId: String,
    val effective: OrganizationAuthSessionPolicyEffectiveDto,
    val guardrails: OrganizationAuthSessionPolicyGuardrailsDto,
    val idpConfigs: List<OrganizationAuthSessionPolicyIdpDto>,
    val hasActiveIdpConfig: Boolean,
)

@Serializable
data class OrganizationAuthSessionPolicyUpdateRequest(
    val accessTokenExpiryMinutes: Long? = null,
    val refreshTokenExpiryMinutes: Long? = null,
    val maxSessionDurationHours: Long? = null,
    val idleTimeoutMinutes: Long? = null,
)

@Serializable
data class LogoutPropagationInfoResponse(
    val platformLogoutAuthoritative: Boolean,
    val idpGlobalLogoutEquivalent: Boolean,
    val message: String,
    val exposureBounds: List<String>,
)

@Serializable
data class ApplicationScopeProbeResponse(
    val endpointGroup: String,
    val authorized: Boolean,
    val issuedAtEpochMillis: Long,
)

@Serializable
data class UserSessionDto(
    val sessionId: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdDate: String,
    val lastSeenAt: String,
    val expiresAt: String? = null,
    val revokedAt: String? = null,
    val revocationReasonCode: String? = null,
    val isActive: Boolean = true,
    /** True for the session that issued the access token used to make the current request. */
    val isCurrent: Boolean = false,
)

@Serializable
data class UserSessionListResponse(
    val sessions: List<UserSessionDto>,
    val total: Int,
)

@Serializable
data class OrgMemberCapacityResponse(
    val organizationId: String,
    val tierCode: String,
    val maxUsers: Long? = null,
    val activeUsers: Long,
    val atCap: Boolean,
    val nearCap: Boolean,
)

/** Grant access on a exchange to a principal (manage-access write API). */
@Serializable
data class GrantSessionShareRequest(
    val principalKind: String,
    val principalId: String,
    val roleName: ExchangeShareRoleName,
    val constraintsJson: String? = null,
    val expiresAtEpochMillis: Long? = null,
)

/** Invite a verified Trusted Organization person or published group as an additional participant. */
@Serializable
data class InviteTrustedParticipantRequest(
    val selection: ExchangeRecipientSelectionRequest,
    val roleName: ExchangeShareRoleName,
    val constraintsJson: String? = null,
    val expiresAtEpochMillis: Long? = null,
)

/** Change the role on an existing session share. */
@Serializable
data class UpdateSessionShareRoleRequest(
    val roleName: ExchangeShareRoleName,
    val constraintsJson: String? = null,
)

/**
 * Replace the pending primary recipient of an Exchange with a freshly resolved selection. Used by
 * the Exchange owner to recover an invitation whose trusted verification, membership, relationship,
 * or policy is no longer valid.
 */
@Serializable
data class ReplacePrimaryRecipientRequest(
    val selection: ExchangeRecipientSelectionRequest,
)

@Serializable
data class ExchangeRecipientInvitationDecisionRequest(
    val decision: ExchangeAcceptanceDecision,
)

