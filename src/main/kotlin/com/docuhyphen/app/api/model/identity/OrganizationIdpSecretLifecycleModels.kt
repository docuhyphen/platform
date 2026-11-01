package com.docuhyphen.app.api.model.identity

data class OrganizationIdpSecretRotationOutcome(
    val secretRef: String,
    val previousVersionId: String?,
    val activeVersionId: String,
    val overlapUntilEpochMillis: Long? = null,
)

data class OrganizationIdpSecretStatusOutcome(
    val secretRef: String,
    val disabled: Boolean,
    val currentVersionId: String? = null,
    val previousVersionId: String? = null,
    val pendingVersionId: String? = null,
    val overlapUntilEpochMillis: Long? = null,
    val rotationPhase: String? = null,
    val overlapActive: Boolean,
)

data class OrganizationIdpSecretRollbackOutcome(
    val secretRef: String,
    val rolledBackToVersionId: String,
    val previousCurrentVersionId: String? = null,
)
