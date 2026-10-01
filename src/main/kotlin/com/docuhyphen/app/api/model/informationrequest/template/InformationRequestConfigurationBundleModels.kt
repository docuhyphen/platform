package com.docuhyphen.app.api.model.informationrequest.template

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestClockDueEffect
import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import kotlinx.serialization.Serializable

@Serializable
data class InformationRequestConfigurationBundle(
    val formatVersion: Int,
    val bundleKey: String,
    val bundleVersion: Int,
    val displayName: String,
    val templateVersions: List<BundleTemplateVersionReference> = emptyList(),
    val validationPolicies: List<BundleValidationPolicy> = emptyList(),
    val reasonCodeVocabularies: List<BundleReasonCodeVocabulary> = emptyList(),
    val rolePresets: List<BundleRolePreset> = emptyList(),
    val clockPolicies: List<BundleClockPolicy> = emptyList(),
    val retentionDefaults: List<BundleRetentionDefault> = emptyList(),
    val connectorContracts: List<BundleConnectorContract> = emptyList(),
)

@Serializable
data class BundleTemplateVersionReference(
    val templateKey: String,
    val versionNumber: Int,
    val contentHashSha256: String,
    val rolePresetKey: String? = null,
    val clockPolicyKey: String? = null,
    val retentionDefaultKey: String? = null,
    val validationPolicyKeys: List<String> = emptyList(),
    val reasonCodeVocabularyKeys: List<String> = emptyList(),
    val connectorKeys: List<String> = emptyList(),
)

enum class BundleValidationTarget
{
    FIELD_VALUE,
    EVIDENCE,
}

enum class BundleValidationRuleKind
{
    MIN_LENGTH,
    MAX_LENGTH,
    PATTERN,
    MIN_VALUE,
    MAX_VALUE,
    ACCEPTED_MEDIA_TYPE,
    MAX_FILE_BYTES,
    MAX_AGE_DAYS,
}

@Serializable
data class BundleValidationRule(
    val kind: BundleValidationRuleKind,
    val parameter: String,
)

@Serializable
data class BundleValidationPolicy(
    val policyKey: String,
    val target: BundleValidationTarget,
    val valueType: FieldValueType? = null,
    val rules: List<BundleValidationRule>,
)

enum class BundleReasonCodePurpose
{
    REVOCATION,
    REVIEW_FINDING,
    CORRECTION,
    WITHDRAWAL,
    WAIVER,
    EXCEPTION,
    CANCELLATION,
    PRIVACY,
}

@Serializable
data class BundleReasonCode(
    val code: String,
    val label: String,
    val retired: Boolean = false,
)

@Serializable
data class BundleReasonCodeVocabulary(
    val vocabularyKey: String,
    val purpose: BundleReasonCodePurpose,
    val codes: List<BundleReasonCode>,
)

@Serializable
data class BundleRolePreset(
    val presetKey: String,
    val displayName: String,
    val roles: List<InformationRequestShareRoleKey>,
    val separatedRoles: List<InformationRequestShareRoleKey> = emptyList(),
)

@Serializable
data class BundleClockPeriod(
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
)

@Serializable
data class BundleClockPolicy(
    val policyKey: String,
    val clockType: InformationRequestClockType,
    val businessTimezone: String,
    val standardDurationMinutes: Int,
    val urgentDurationMinutes: Int,
    val escalationAfterMinutes: Int? = null,
    val dueEffect: InformationRequestClockDueEffect,
    val businessPeriods: List<BundleClockPeriod> = emptyList(),
    val holidays: List<String> = emptyList(),
    val reminderOffsetsMinutes: List<Int> = emptyList(),
)

@Serializable
data class BundleRetentionDefault(
    val retentionKey: String,
    val minimumRetentionDays: Int,
    val disposalAfterDays: Int? = null,
)

enum class BundleConnectorKind
{
    STRUCTURED_EVIDENCE,
    EXTERNAL_VERIFICATION,
}

@Serializable
data class BundleConnectorContract(
    val connectorKey: String,
    val kind: BundleConnectorKind,
    val contractVersion: Int,
    val resultKeys: List<String>,
    val maximumResultAgeDays: Int? = null,
)

data class InformationRequestConfigurationBundleProblem(
    val path: String,
    val code: String,
    val message: String,
)

sealed interface InformationRequestConfigurationBundleParse
{
    data class Parsed(val bundle: InformationRequestConfigurationBundle) : InformationRequestConfigurationBundleParse

    data class Refused(val problems: List<InformationRequestConfigurationBundleProblem>) :
        InformationRequestConfigurationBundleParse
}
