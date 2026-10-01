package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestClockDueEffect
import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.template.BundleClockPeriod
import com.docuhyphen.app.api.model.informationrequest.template.BundleClockPolicy
import com.docuhyphen.app.api.model.informationrequest.template.BundleConnectorContract
import com.docuhyphen.app.api.model.informationrequest.template.BundleConnectorKind
import com.docuhyphen.app.api.model.informationrequest.template.BundleReasonCode
import com.docuhyphen.app.api.model.informationrequest.template.BundleReasonCodePurpose
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class InformationRequestConfigurationBundleValidatorTest
{
    private val validator = InformationRequestConfigurationBundleValidator()

    @Test
    fun `a staged multi-party bundle and a recurring single-party bundle both validate and round-trip`()
    {
        listOf(stagedMultiPartyBundle(), recurringSinglePartyBundle()).forEach { bundle ->
            assertEquals(emptyList<Any>(), validator.validate(bundle), bundle.bundleKey)
            val parsed = validator.parse(Json.encodeToString(bundle))
            assertEquals(InformationRequestConfigurationBundleParse.Parsed(bundle), parsed)
        }
    }

    @Test
    fun `parsing refuses unknown sections, unsupported format versions, and malformed documents`()
    {
        val unknown = Json.encodeToString(stagedMultiPartyBundle()).replaceFirst("{", "{\"decisionRules\":[],")
        assertEquals(listOf("bundle.unreadable"), codes(validator.parse(unknown)))
        val future = Json.encodeToString(stagedMultiPartyBundle().copy(formatVersion = 2))
        assertEquals(listOf("bundle.format_version_unsupported"), codes(validator.parse(future)))
        assertEquals(listOf("bundle.unreadable"), codes(validator.parse("{not json")))
    }

    @Test
    fun `a connector contract names its result keys and cannot opt out of review`()
    {
        val encoded = Json.encodeToString(recurringSinglePartyBundle())
        assertTrue(encoded.contains("\"resultKeys\":[\"verified-status\",\"verified-on\"]"), encoded)
        val optedOut = encoded.replace("\"resultKeys\":", "\"requiresReview\":false,\"resultKeys\":")
        assertEquals(listOf("bundle.unreadable"), codes(validator.parse(optedOut)))
    }

    @Test
    fun `structural problems are reported with the path of every offending element`()
    {
        val bundle = stagedMultiPartyBundle().copy(
            bundleKey = "Not A Key",
            bundleVersion = 0,
            validationPolicies = listOf(
                BundleValidationPolicy(
                    policyKey = "text-shape",
                    target = BundleValidationTarget.FIELD_VALUE,
                    valueType = FieldValueType.SHORT_TEXT,
                    rules = listOf(
                        BundleValidationRule(BundleValidationRuleKind.PATTERN, "(unclosed"),
                        BundleValidationRule(BundleValidationRuleKind.MAX_LENGTH, "many"),
                        BundleValidationRule(BundleValidationRuleKind.ACCEPTED_MEDIA_TYPE, "application/pdf"),
                    ),
                ),
                BundleValidationPolicy("text-shape", BundleValidationTarget.EVIDENCE, rules = emptyList()),
            ),
            reasonCodeVocabularies = listOf(
                BundleReasonCodeVocabulary(
                    "review-reasons",
                    BundleReasonCodePurpose.REVIEW_FINDING,
                    listOf(BundleReasonCode("missing-item", "Missing item"), BundleReasonCode("missing-item", " ")),
                ),
            ),
            rolePresets = listOf(BundleRolePreset("empty-preset", "Empty", emptyList())),
            clockPolicies = listOf(
                BundleClockPolicy(
                    policyKey = "response-window",
                    clockType = InformationRequestClockType.CALENDAR,
                    businessTimezone = "Nowhere/Invalid",
                    standardDurationMinutes = 0,
                    urgentDurationMinutes = 60,
                    dueEffect = InformationRequestClockDueEffect.MARK_OVERDUE,
                    businessPeriods = listOf(BundleClockPeriod(8, 600, 540)),
                    holidays = listOf("2026-13-40"),
                ),
            ),
            retentionDefaults = listOf(BundleRetentionDefault("record-retention", 30, 10)),
            connectorContracts = listOf(BundleConnectorContract("verification", BundleConnectorKind.EXTERNAL_VERIFICATION, 0, emptyList())),
            templateVersions = listOf(BundleTemplateVersionReference("intake-template", 1, "abc", rolePresetKey = "absent")),
        )

        val problems = validator.validate(bundle).map { "${it.path}:${it.code}" }.toSet()

        assertEquals(
            setOf(
                "bundleKey:key.malformed",
                "bundleVersion:version.invalid",
                "validationPolicies[0].rules[0]:rule.parameter_invalid",
                "validationPolicies[0].rules[1]:rule.parameter_invalid",
                "validationPolicies[0].rules[2]:rule.target_mismatch",
                "validationPolicies[1]:key.duplicate",
                "validationPolicies[1].rules:collection.empty",
                "reasonCodeVocabularies[0].codes[1]:key.duplicate",
                "reasonCodeVocabularies[0].codes[1].label:label.blank",
                "rolePresets[0].roles:collection.empty",
                "clockPolicies[0].businessTimezone:timezone.unknown",
                "clockPolicies[0].standardDurationMinutes:duration.invalid",
                "clockPolicies[0].urgentDurationMinutes:duration.invalid",
                "clockPolicies[0].businessPeriods:period.not_applicable",
                "clockPolicies[0].businessPeriods[0]:period.invalid",
                "clockPolicies[0].holidays[0]:date.invalid",
                "retentionDefaults[0].disposalAfterDays:retention.invalid",
                "connectorContracts[0].contractVersion:version.invalid",
                "connectorContracts[0].resultKeys:collection.empty",
                "templateVersions[0].contentHashSha256:hash.malformed",
                "templateVersions[0].rolePresetKey:reference.unknown",
            ),
            problems,
        )
    }

    @Test
    fun `the platform ships no configuration bundle among its production resources`()
    {
        val shipped = File("src/main/resources").walkTopDown()
            .filter { it.isFile }
            .filter { file -> file.extension in setOf("json", "yaml", "yml") && file.readText().contains("\"formatVersion\"") }
            .map { it.path }
            .toList()
        assertTrue(shipped.isEmpty(), shipped.toString())
    }

    private fun codes(parse: InformationRequestConfigurationBundleParse): List<String> =
        (parse as InformationRequestConfigurationBundleParse.Refused).problems.map { it.code }

    private fun stagedMultiPartyBundle() = InformationRequestConfigurationBundle(
        formatVersion = 1,
        bundleKey = "staged-collection",
        bundleVersion = 3,
        displayName = "Staged collection",
        templateVersions = listOf(
            BundleTemplateVersionReference(
                templateKey = "staged-intake",
                versionNumber = 2,
                contentHashSha256 = "a".repeat(64),
                rolePresetKey = "separated-review",
                clockPolicyKey = "business-window",
                retentionDefaultKey = "standard-retention",
                validationPolicyKeys = listOf("record-files", "short-answer"),
                reasonCodeVocabularyKeys = listOf("review-reasons"),
            ),
        ),
        validationPolicies = listOf(
            BundleValidationPolicy(
                policyKey = "short-answer",
                target = BundleValidationTarget.FIELD_VALUE,
                valueType = FieldValueType.SHORT_TEXT,
                rules = listOf(
                    BundleValidationRule(BundleValidationRuleKind.MIN_LENGTH, "2"),
                    BundleValidationRule(BundleValidationRuleKind.MAX_LENGTH, "120"),
                    BundleValidationRule(BundleValidationRuleKind.PATTERN, "^[A-Za-z0-9 .-]+$"),
                ),
            ),
            BundleValidationPolicy(
                policyKey = "record-files",
                target = BundleValidationTarget.EVIDENCE,
                rules = listOf(
                    BundleValidationRule(BundleValidationRuleKind.ACCEPTED_MEDIA_TYPE, "application/pdf"),
                    BundleValidationRule(BundleValidationRuleKind.ACCEPTED_MEDIA_TYPE, "image/png"),
                    BundleValidationRule(BundleValidationRuleKind.MAX_FILE_BYTES, "10485760"),
                    BundleValidationRule(BundleValidationRuleKind.MAX_AGE_DAYS, "90"),
                ),
            ),
        ),
        reasonCodeVocabularies = listOf(
            BundleReasonCodeVocabulary(
                vocabularyKey = "review-reasons",
                purpose = BundleReasonCodePurpose.REVIEW_FINDING,
                codes = listOf(
                    BundleReasonCode("missing-item", "An item is missing"),
                    BundleReasonCode("unreadable-file", "A file cannot be read"),
                    BundleReasonCode("superseded-code", "Replaced reason", retired = true),
                ),
            ),
        ),
        rolePresets = listOf(
            BundleRolePreset(
                presetKey = "separated-review",
                displayName = "Separated review",
                roles = listOf(
                    InformationRequestShareRoleKey.SUBJECT,
                    InformationRequestShareRoleKey.CONTRIBUTOR,
                    InformationRequestShareRoleKey.ATTESTOR,
                    InformationRequestShareRoleKey.REVIEWER,
                ),
                separatedRoles = listOf(InformationRequestShareRoleKey.CONTRIBUTOR, InformationRequestShareRoleKey.REVIEWER),
            ),
        ),
        clockPolicies = listOf(
            BundleClockPolicy(
                policyKey = "business-window",
                clockType = InformationRequestClockType.BUSINESS,
                businessTimezone = "Europe/Paris",
                standardDurationMinutes = 7200,
                urgentDurationMinutes = 1440,
                escalationAfterMinutes = 8640,
                dueEffect = InformationRequestClockDueEffect.MARK_OVERDUE,
                businessPeriods = (1..5).map { BundleClockPeriod(it, 540, 1020) },
                holidays = listOf("2026-12-25"),
                reminderOffsetsMinutes = listOf(1440, 60),
            ),
        ),
        retentionDefaults = listOf(BundleRetentionDefault("standard-retention", 365, 730)),
    )

    private fun recurringSinglePartyBundle() = InformationRequestConfigurationBundle(
        formatVersion = 1,
        bundleKey = "periodic-confirmation",
        bundleVersion = 1,
        displayName = "Periodic confirmation",
        templateVersions = listOf(
            BundleTemplateVersionReference(
                templateKey = "periodic-update",
                versionNumber = 5,
                contentHashSha256 = "b".repeat(64),
                rolePresetKey = "single-respondent",
                clockPolicyKey = "calendar-window",
                connectorKeys = listOf("record-verification"),
            ),
        ),
        rolePresets = listOf(
            BundleRolePreset("single-respondent", "Single respondent", listOf(InformationRequestShareRoleKey.SUBJECT)),
        ),
        clockPolicies = listOf(
            BundleClockPolicy(
                policyKey = "calendar-window",
                clockType = InformationRequestClockType.CALENDAR,
                businessTimezone = "UTC",
                standardDurationMinutes = 20160,
                urgentDurationMinutes = 4320,
                dueEffect = InformationRequestClockDueEffect.EXPIRE_REQUEST,
            ),
        ),
        connectorContracts = listOf(
            BundleConnectorContract(
                connectorKey = "record-verification",
                kind = BundleConnectorKind.EXTERNAL_VERIFICATION,
                contractVersion = 1,
                resultKeys = listOf("verified-status", "verified-on"),
                maximumResultAgeDays = 30,
            ),
        ),
    )
}
