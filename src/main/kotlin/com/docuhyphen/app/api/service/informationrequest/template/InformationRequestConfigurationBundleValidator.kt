package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.informationrequest.template.BundleClockPolicy
import com.docuhyphen.app.api.model.informationrequest.template.BundleConnectorContract
import com.docuhyphen.app.api.model.informationrequest.template.BundleReasonCodeVocabulary
import com.docuhyphen.app.api.model.informationrequest.template.BundleRetentionDefault
import com.docuhyphen.app.api.model.informationrequest.template.BundleRolePreset
import com.docuhyphen.app.api.model.informationrequest.template.BundleTemplateVersionReference
import com.docuhyphen.app.api.model.informationrequest.template.BundleValidationPolicy
import com.docuhyphen.app.api.model.informationrequest.template.BundleValidationRule
import com.docuhyphen.app.api.model.informationrequest.template.BundleValidationRuleKind
import com.docuhyphen.app.api.model.informationrequest.template.BundleValidationTarget
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestConfigurationBundle
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestConfigurationBundleParse
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestConfigurationBundleProblem
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.util.regex.PatternSyntaxException

@ApplicationScoped
class InformationRequestConfigurationBundleValidator
{
    fun parse(document: String): InformationRequestConfigurationBundleParse
    {
        val bundle = try
        {
            STRICT_JSON.decodeFromString(InformationRequestConfigurationBundle.serializer(), document)
        }
        catch (exception: SerializationException)
        {
            return refused("", "bundle.unreadable", exception.message ?: "The bundle is not a readable configuration bundle")
        }
        catch (exception: IllegalArgumentException)
        {
            return refused("", "bundle.unreadable", exception.message ?: "The bundle is not a readable configuration bundle")
        }
        if (bundle.formatVersion != SUPPORTED_FORMAT_VERSION)
        {
            return refused("formatVersion", "bundle.format_version_unsupported", "Only format version $SUPPORTED_FORMAT_VERSION is supported")
        }
        return InformationRequestConfigurationBundleParse.Parsed(bundle)
    }

    fun check(document: String): List<InformationRequestConfigurationBundleProblem> =
        when (val parsed = parse(document))
        {
            is InformationRequestConfigurationBundleParse.Parsed -> validate(parsed.bundle)
            is InformationRequestConfigurationBundleParse.Refused -> parsed.problems
        }

    fun validate(bundle: InformationRequestConfigurationBundle): List<InformationRequestConfigurationBundleProblem>
    {
        val problems = Problems()
        if (bundle.formatVersion != SUPPORTED_FORMAT_VERSION)
        {
            problems.add("formatVersion", "bundle.format_version_unsupported", "Only format version $SUPPORTED_FORMAT_VERSION is supported")
        }
        problems.key("bundleKey", bundle.bundleKey)
        if (bundle.bundleVersion < 1) problems.add("bundleVersion", "version.invalid", "A version is a positive number")
        problems.label("displayName", bundle.displayName)
        validationPolicies(problems, bundle.validationPolicies)
        reasonCodes(problems, bundle.reasonCodeVocabularies)
        rolePresets(problems, bundle.rolePresets)
        clocks(problems, bundle.clockPolicies)
        retention(problems, bundle.retentionDefaults)
        connectors(problems, bundle.connectorContracts)
        templateVersions(problems, bundle)
        return problems.all
    }

    private fun validationPolicies(problems: Problems, policies: List<BundleValidationPolicy>)
    {
        problems.uniqueKeys("validationPolicies", policies.map { it.policyKey })
        policies.forEachIndexed { index, policy ->
            val path = "validationPolicies[$index]"
            if (policy.target == BundleValidationTarget.FIELD_VALUE && policy.valueType == null)
            {
                problems.add("$path.valueType", "policy.value_type_required", "A Field value policy names the value type it checks")
            }
            if (policy.rules.isEmpty()) problems.add("$path.rules", "collection.empty", "A policy states at least one rule")
            policy.rules.forEachIndexed { ruleIndex, rule -> rule(problems, "$path.rules[$ruleIndex]", policy.target, rule) }
        }
    }

    private fun rule(problems: Problems, path: String, target: BundleValidationTarget, rule: BundleValidationRule)
    {
        if (rule.kind !in RULES_BY_TARGET.getValue(target))
        {
            problems.add(path, "rule.target_mismatch", "A ${rule.kind} rule does not apply to $target policies")
            return
        }
        val valid = when (rule.kind)
        {
            BundleValidationRuleKind.MIN_LENGTH,
            BundleValidationRuleKind.MAX_LENGTH,
            BundleValidationRuleKind.MAX_FILE_BYTES,
            BundleValidationRuleKind.MAX_AGE_DAYS,
            -> rule.parameter.toLongOrNull()?.let { it >= 0 } == true
            BundleValidationRuleKind.MIN_VALUE,
            BundleValidationRuleKind.MAX_VALUE,
            -> rule.parameter.toBigDecimalOrNull() != null
            BundleValidationRuleKind.PATTERN -> compiles(rule.parameter)
            BundleValidationRuleKind.ACCEPTED_MEDIA_TYPE -> MEDIA_TYPE.matches(rule.parameter)
        }
        if (!valid) problems.add(path, "rule.parameter_invalid", "The ${rule.kind} rule parameter is not valid")
    }

    private fun reasonCodes(problems: Problems, vocabularies: List<BundleReasonCodeVocabulary>)
    {
        problems.uniqueKeys("reasonCodeVocabularies", vocabularies.map { it.vocabularyKey })
        vocabularies.forEachIndexed { index, vocabulary ->
            val path = "reasonCodeVocabularies[$index]"
            if (vocabulary.codes.isEmpty()) problems.add("$path.codes", "collection.empty", "A vocabulary lists at least one code")
            problems.uniqueKeys("$path.codes", vocabulary.codes.map { it.code })
            vocabulary.codes.forEachIndexed { codeIndex, code -> problems.label("$path.codes[$codeIndex].label", code.label) }
        }
    }

    private fun rolePresets(problems: Problems, presets: List<BundleRolePreset>)
    {
        problems.uniqueKeys("rolePresets", presets.map { it.presetKey })
        presets.forEachIndexed { index, preset ->
            val path = "rolePresets[$index]"
            problems.label("$path.displayName", preset.displayName)
            if (preset.roles.isEmpty()) problems.add("$path.roles", "collection.empty", "A preset assigns at least one role")
            if (preset.roles.size != preset.roles.toSet().size) problems.add("$path.roles", "collection.duplicate", "A role is listed once")
            if (!preset.roles.containsAll(preset.separatedRoles))
            {
                problems.add("$path.separatedRoles", "reference.unknown", "Separated roles are roles the preset assigns")
            }
        }
    }

    private fun clocks(problems: Problems, clocks: List<BundleClockPolicy>)
    {
        problems.uniqueKeys("clockPolicies", clocks.map { it.policyKey })
        clocks.forEachIndexed { index, clock ->
            val path = "clockPolicies[$index]"
            if (!knownZone(clock.businessTimezone)) problems.add("$path.businessTimezone", "timezone.unknown", "The time zone is not a known region")
            if (clock.standardDurationMinutes <= 0) problems.add("$path.standardDurationMinutes", "duration.invalid", "A duration is positive")
            if (clock.urgentDurationMinutes <= 0 || clock.urgentDurationMinutes > clock.standardDurationMinutes)
            {
                problems.add("$path.urgentDurationMinutes", "duration.invalid", "An urgent duration is positive and no longer than the standard one")
            }
            if (clock.escalationAfterMinutes?.let { it <= 0 } == true)
            {
                problems.add("$path.escalationAfterMinutes", "duration.invalid", "An escalation delay is positive")
            }
            if (clock.clockType == InformationRequestClockType.CALENDAR && clock.businessPeriods.isNotEmpty())
            {
                problems.add("$path.businessPeriods", "period.not_applicable", "A calendar clock has no business periods")
            }
            if (clock.clockType == InformationRequestClockType.BUSINESS && clock.businessPeriods.isEmpty())
            {
                problems.add("$path.businessPeriods", "collection.empty", "A business clock names its business periods")
            }
            clock.businessPeriods.forEachIndexed { periodIndex, period ->
                if (period.dayOfWeek !in 1..7 || period.startMinute !in 0 until MINUTES_PER_DAY ||
                    period.endMinute !in 1..MINUTES_PER_DAY || period.endMinute <= period.startMinute)
                {
                    problems.add("$path.businessPeriods[$periodIndex]", "period.invalid", "A period is a day and an increasing minute range")
                }
            }
            clock.holidays.forEachIndexed { holidayIndex, holiday ->
                if (!isDate(holiday)) problems.add("$path.holidays[$holidayIndex]", "date.invalid", "A holiday is an ISO calendar date")
            }
            clock.reminderOffsetsMinutes.forEachIndexed { reminderIndex, offset ->
                if (offset <= 0) problems.add("$path.reminderOffsetsMinutes[$reminderIndex]", "duration.invalid", "A reminder offset is positive")
            }
        }
    }

    private fun retention(problems: Problems, defaults: List<BundleRetentionDefault>)
    {
        problems.uniqueKeys("retentionDefaults", defaults.map { it.retentionKey })
        defaults.forEachIndexed { index, default ->
            val path = "retentionDefaults[$index]"
            if (default.minimumRetentionDays < 0) problems.add("$path.minimumRetentionDays", "retention.invalid", "A retention period is not negative")
            if (default.disposalAfterDays?.let { it < default.minimumRetentionDays } == true)
            {
                problems.add("$path.disposalAfterDays", "retention.invalid", "Disposal comes no earlier than the minimum retention")
            }
        }
    }

    private fun connectors(problems: Problems, connectors: List<BundleConnectorContract>)
    {
        problems.uniqueKeys("connectorContracts", connectors.map { it.connectorKey })
        connectors.forEachIndexed { index, connector ->
            val path = "connectorContracts[$index]"
            if (connector.contractVersion < 1) problems.add("$path.contractVersion", "version.invalid", "A version is a positive number")
            if (connector.resultKeys.isEmpty()) problems.add("$path.resultKeys", "collection.empty", "A connector names the values it returns")
            problems.uniqueKeys("$path.resultKeys", connector.resultKeys)
            if (connector.maximumResultAgeDays?.let { it <= 0 } == true)
            {
                problems.add("$path.maximumResultAgeDays", "duration.invalid", "A result age limit is positive")
            }
        }
    }

    private fun templateVersions(problems: Problems, bundle: InformationRequestConfigurationBundle)
    {
        problems.uniqueKeys("templateVersions", bundle.templateVersions.map { "${it.templateKey}@${it.versionNumber}" }, validateShape = false)
        bundle.templateVersions.forEachIndexed { index, reference ->
            val path = "templateVersions[$index]"
            problems.key("$path.templateKey", reference.templateKey)
            if (reference.versionNumber < 1) problems.add("$path.versionNumber", "version.invalid", "A version is a positive number")
            if (!SHA256.matches(reference.contentHashSha256))
            {
                problems.add("$path.contentHashSha256", "hash.malformed", "A content hash is 64 lowercase hexadecimal characters")
            }
            references(problems, path, reference, bundle)
        }
    }

    private fun references(problems: Problems, path: String, reference: BundleTemplateVersionReference, bundle: InformationRequestConfigurationBundle)
    {
        fun require(field: String, key: String?, known: Collection<String>)
        {
            if (key != null && key !in known) problems.add("$path.$field", "reference.unknown", "$key is not defined in this bundle")
        }
        require("rolePresetKey", reference.rolePresetKey, bundle.rolePresets.map { it.presetKey })
        require("clockPolicyKey", reference.clockPolicyKey, bundle.clockPolicies.map { it.policyKey })
        require("retentionDefaultKey", reference.retentionDefaultKey, bundle.retentionDefaults.map { it.retentionKey })
        reference.validationPolicyKeys.forEachIndexed { index, key ->
            require("validationPolicyKeys[$index]", key, bundle.validationPolicies.map { it.policyKey })
        }
        reference.reasonCodeVocabularyKeys.forEachIndexed { index, key ->
            require("reasonCodeVocabularyKeys[$index]", key, bundle.reasonCodeVocabularies.map { it.vocabularyKey })
        }
        reference.connectorKeys.forEachIndexed { index, key ->
            require("connectorKeys[$index]", key, bundle.connectorContracts.map { it.connectorKey })
        }
    }

    private fun compiles(pattern: String): Boolean =
        try
        {
            Regex(pattern)
            pattern.isNotEmpty()
        }
        catch (_: PatternSyntaxException)
        {
            false
        }

    private fun knownZone(zone: String): Boolean =
        try
        {
            ZoneId.of(zone)
            true
        }
        catch (_: DateTimeException)
        {
            false
        }

    private fun isDate(value: String): Boolean =
        try
        {
            LocalDate.parse(value)
            true
        }
        catch (_: DateTimeException)
        {
            false
        }

    private fun refused(path: String, code: String, message: String): InformationRequestConfigurationBundleParse =
        InformationRequestConfigurationBundleParse.Refused(listOf(InformationRequestConfigurationBundleProblem(path, code, message)))

    private class Problems
    {
        val all = mutableListOf<InformationRequestConfigurationBundleProblem>()

        fun add(path: String, code: String, message: String)
        {
            all += InformationRequestConfigurationBundleProblem(path, code, message)
        }

        fun key(path: String, key: String)
        {
            if (!MACHINE_KEY.matches(key)) add(path, "key.malformed", "A key is a lowercase machine key")
        }

        fun label(path: String, label: String)
        {
            if (label.isBlank()) add(path, "label.blank", "A label is not blank")
            else if (label.length > MAXIMUM_LABEL_LENGTH) add(path, "label.too_long", "A label is at most $MAXIMUM_LABEL_LENGTH characters")
        }

        fun uniqueKeys(path: String, keys: List<String>, validateShape: Boolean = true)
        {
            val seen = mutableSetOf<String>()
            keys.forEachIndexed { index, key ->
                val element = "$path[$index]"
                if (validateShape && !MACHINE_KEY.matches(key)) add(element, "key.malformed", "A key is a lowercase machine key")
                if (!seen.add(key)) add(element, "key.duplicate", "$key is defined more than once")
            }
        }
    }

    private companion object
    {
        const val SUPPORTED_FORMAT_VERSION = 1
        const val MINUTES_PER_DAY = 1440
        const val MAXIMUM_LABEL_LENGTH = 200
        val MACHINE_KEY = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
        val SHA256 = Regex("^[0-9a-f]{64}$")
        val MEDIA_TYPE = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]{0,126}/[a-z0-9*][a-z0-9!#$&^_.+*-]{0,126}$")
        val STRICT_JSON = Json { ignoreUnknownKeys = false }
        val RULES_BY_TARGET = mapOf(
            BundleValidationTarget.FIELD_VALUE to setOf(
                BundleValidationRuleKind.MIN_LENGTH,
                BundleValidationRuleKind.MAX_LENGTH,
                BundleValidationRuleKind.PATTERN,
                BundleValidationRuleKind.MIN_VALUE,
                BundleValidationRuleKind.MAX_VALUE,
            ),
            BundleValidationTarget.EVIDENCE to setOf(
                BundleValidationRuleKind.ACCEPTED_MEDIA_TYPE,
                BundleValidationRuleKind.MAX_FILE_BYTES,
                BundleValidationRuleKind.MAX_AGE_DAYS,
            ),
        )
    }
}
