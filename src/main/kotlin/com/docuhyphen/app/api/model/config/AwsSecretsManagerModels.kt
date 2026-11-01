package com.docuhyphen.app.api.model.config

data class SecretVersionInfo(
    val versionId: String,
    val stages: Set<String>,
)

data class SecretRotationResult(
    val secretId: String,
    val previousVersionId: String?,
    val activeVersionId: String,
    val overlapUntilEpochMillis: Long? = null,
)

data class SecretLifecycleStatus(
    val secretId: String,
    val disabled: Boolean,
    val currentVersionId: String? = null,
    val previousVersionId: String? = null,
    val pendingVersionId: String? = null,
    val overlapUntilEpochMillis: Long? = null,
    val rotationPhase: String? = null,
)
