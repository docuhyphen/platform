package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAcceptedValueRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.service.fields.FieldOperator
import com.docuhyphen.app.api.service.fields.FieldTypeRegistry
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.json.JsonNull
import java.util.UUID

/**
 * Checks that one authored draft configuration holds together on its own terms, and returns the one
 * normalized reading of it that gets written.
 *
 * Two kinds of rule live here and nothing else does. The first is normalization: a machine
 * identifier means the same thing however it was typed, a repeated answer is one answer, and
 * The second is coherence between the parts
 * of one document: keys that have to be distinct, references that have to resolve, relations
 * that only hold between certain kinds of requirement, and policies stated about one requirement
 * that have to be reachable by the party that requirement nominates. Both are refused with the
 * offending key named, because the stored constraints that also refuse them can only report a
 * constraint name.
 *
 * Bounds that contradict each other are deliberately absent. Those are facts about one stored
 * policy row and the row already refuses them; restating them here would create a second rule to
 * keep in step with the first.
 */
@ApplicationScoped
class InformationRequestTemplateConfigurationValidator
{
    private val fieldTypeRegistry = FieldTypeRegistry()

    /**
     * @throws InformationRequestTemplateValidationException when the document does not hold
     * together. The returned document is the only reading of the input that gets written.
     */
    fun normalize(
        request: InformationRequestTemplateConfigurationRequest,
    ): InformationRequestTemplateConfigurationRequest
    {
        val conditionRules = normalizeConditionRules(request.conditionRules)
        val groups = normalizeGroups(request.groups)
        val groupKeys = groups.map { it.groupKey }.toSet()

        val sections = request.sections.map(::normalizeSection)
        requireDistinct(sections.map { it.sectionKey }) { key ->
            refuse("Two sections state the same section key: $key", sectionKey = key)
        }

        val requirements = sections.flatMap { it.requirements }
        requireDistinct(requirements.map { it.requirementKey }) { key ->
            refuse(
                "One template version states one thing about one requirement, and $key is stated twice",
                requirementKey = key,
            )
        }

        val typeByKey = requirements.associate { it.requirementKey to it.requirementType }
        requirements.forEach { validateRelations(it, typeByKey) }
        validateSubstitutionIsFlat(requirements)
        validateEachFieldIsCollectedOnce(requirements)
        requirements.forEach { validateOccurrenceAnchor(it, groupKeys) }
        validateConditionRules(requirements, conditionRules)

        return InformationRequestTemplateSubmissionPolicyValidator.normalize(
            request.copy(sections = sections, groups = groups, conditionRules = conditionRules),
        )
    }

    private fun normalizeConditionRules(
        conditionRules: List<InformationRequestTemplateConditionRuleRequest>,
    ): List<InformationRequestTemplateConditionRuleRequest>
    {
        val normalized = conditionRules.map { rule ->
            val ruleKey = normalizeMachineKey(rule.ruleKey) {
                refuseConditionRule(machineKeyRefusal("condition rule key", rule.ruleKey), rule.ruleKey)
            }
            if (rule.expressionVersion != SUPPORTED_CONDITION_EXPRESSION_VERSION)
            {
                refuseConditionRule(
                    "Condition rule $ruleKey uses expression version ${rule.expressionVersion}, which this runtime " +
                        "does not support",
                    ruleKey,
                )
            }
            if (rule.predicates.isEmpty())
            {
                refuseConditionRule("Condition rule $ruleKey states no predicates", ruleKey)
            }
            rule.copy(
                ruleKey = ruleKey,
                predicates = rule.predicates.map { normalizeConditionPredicate(ruleKey, it) },
            )
        }
        requireDistinct(normalized.map { it.ruleKey }) { key ->
            refuseConditionRule("Two condition rules state the same rule key: $key", key)
        }
        return normalized
    }

    private fun normalizeConditionPredicate(
        ruleKey: String,
        predicate: InformationRequestTemplateConditionPredicateRequest,
    ): InformationRequestTemplateConditionPredicateRequest
    {
        val sourceRequirementKey = optionalContent(predicate.sourceRequirementKey)?.let { candidate ->
            normalizeMachineKey(candidate) {
                refuseConditionRule(
                    "Condition rule $ruleKey names '$candidate' as a source requirement, which is not a usable " +
                        "requirement key",
                    ruleKey,
                )
            }
        }
        val hasRequirement = sourceRequirementKey != null
        val hasField = predicate.fieldDefinitionId != null
        if (hasRequirement == hasField)
        {
            refuseConditionRule(
                "Condition rule $ruleKey must name exactly one source requirement or Field",
                ruleKey,
            )
        }
        if (hasField)
        {
            val valueType = predicate.valueType
                ?: refuseConditionRule("Condition rule $ruleKey names a Field but no Field value type", ruleKey)
            val supported = fieldTypeRegistry.contractFor(valueType).supportedOperators
            if (predicate.operator !in supported)
            {
                refuseConditionRule(
                    "Condition rule $ruleKey uses ${predicate.operator} with $valueType, which is not supported",
                    ruleKey,
                )
            }
            validateConditionValue(ruleKey, predicate)
        }
        else
        {
            if (predicate.operator !in DISPOSITION_OPERATORS)
            {
                refuseConditionRule(
                    "Condition rule $ruleKey uses ${predicate.operator} against a requirement disposition",
                    ruleKey,
                )
            }
            if (predicate.operator in DISPOSITION_VALUE_OPERATORS && predicate.expectedDisposition == null)
            {
                refuseConditionRule("Condition rule $ruleKey compares a disposition but states no disposition", ruleKey)
            }
        }
        return predicate.copy(sourceRequirementKey = sourceRequirementKey)
    }

    private fun validateConditionValue(
        ruleKey: String,
        predicate: InformationRequestTemplateConditionPredicateRequest,
    )
    {
        if (predicate.operator !in VALUELESS_CONDITION_OPERATORS && (predicate.value == null || predicate.value is JsonNull))
        {
            refuseConditionRule("Condition rule $ruleKey compares a Field but states no value", ruleKey)
        }
    }

    private fun validateConditionRules(
        requirements: List<InformationRequestTemplateRequirementRequest>,
        conditionRules: List<InformationRequestTemplateConditionRuleRequest>,
    )
    {
        val ruleByKey = conditionRules.associateBy { it.ruleKey }
        val requirementByKey = requirements.associateBy { it.requirementKey }
        val requirementKeyByField = requirements
            .mapNotNull { requirement ->
                requirement.collectedFieldDefinitionId?.let { it to requirement.requirementKey }
            }
            .toMap()

        requirements.forEach { requirement ->
            val ruleKey = requirement.conditionalRuleKey ?: return@forEach
            if (ruleKey !in ruleByKey)
            {
                refuse(
                    "Requirement ${requirement.requirementKey} names condition rule $ruleKey, which this template " +
                        "version does not define",
                    requirementKey = requirement.requirementKey,
                )
            }
        }

        conditionRules.forEach { rule ->
            rule.predicates.forEach { predicate ->
                predicate.sourceRequirementKey?.let { sourceKey ->
                    if (sourceKey !in requirementByKey)
                    {
                        refuseConditionRule(
                            "Condition rule ${rule.ruleKey} reads requirement $sourceKey, which this template " +
                                "version does not ask for",
                            rule.ruleKey,
                        )
                    }
                }
                predicate.fieldDefinitionId?.let { fieldId ->
                    if (fieldId !in requirementKeyByField)
                    {
                        refuseConditionRule(
                            "Condition rule ${rule.ruleKey} reads Field $fieldId, which no requirement in this " +
                                "template version collects",
                            rule.ruleKey,
                        )
                    }
                }
            }
        }

        detectConditionCycles(requirements, ruleByKey, requirementKeyByField)
    }

    private fun detectConditionCycles(
        requirements: List<InformationRequestTemplateRequirementRequest>,
        ruleByKey: Map<String, InformationRequestTemplateConditionRuleRequest>,
        requirementKeyByField: Map<UUID, String>,
    )
    {
        val conditionalByKey = requirements
            .filter { it.requiredness == InformationRequestRequiredness.CONDITIONAL && it.conditionalRuleKey != null }
            .associateBy { it.requirementKey }
        val dependencies = conditionalByKey.mapValues { (_, requirement) ->
            ruleByKey.getValue(requirement.conditionalRuleKey!!).predicates.mapNotNull { predicate ->
                predicate.sourceRequirementKey ?: predicate.fieldDefinitionId?.let(requirementKeyByField::get)
            }.filter { it in conditionalByKey }
        }

        fun visit(key: String, path: List<String>)
        {
            if (key in path)
            {
                refuse(
                    "Conditional requirements ${path.dropWhile { it != key }.plus(key).joinToString(" then ")} " +
                        "depend on each other",
                    requirementKey = key,
                )
            }
            dependencies[key].orEmpty().forEach { dependency -> visit(dependency, path + key) }
        }

        dependencies.keys.forEach { visit(it, emptyList()) }
    }

    // ── The repeatable and nested groups one document defines ──────────────────

    private fun normalizeGroups(
        groups: List<InformationRequestTemplateGroupRequest>,
    ): List<InformationRequestTemplateGroupRequest>
    {
        val normalized = groups.map(::normalizeGroup)
        requireDistinct(normalized.map { it.groupKey }) { key ->
            refuseGroup("Two repeatable groups state the same group key: $key", key)
        }

        val byKey = normalized.associateBy { it.groupKey }
        normalized.forEach { group ->
            val parentKey = group.parentGroupKey ?: return@forEach
            if (parentKey == group.groupKey)
            {
                refuseGroup(
                    "Repeatable group ${group.groupKey} names itself as its own parent",
                    group.groupKey,
                )
            }
            if (parentKey !in byKey)
            {
                refuseGroup(
                    "Repeatable group ${group.groupKey} names parent group $parentKey, which this " +
                        "template version does not define",
                    group.groupKey,
                )
            }
        }
        detectGroupNestingCycles(normalized, byKey)

        return normalized
    }

    private fun normalizeGroup(
        group: InformationRequestTemplateGroupRequest,
    ): InformationRequestTemplateGroupRequest
    {
        val groupKey = normalizeMachineKey(group.groupKey) {
            refuseGroup(machineKeyRefusal("repeatable group key", group.groupKey), group.groupKey)
        }
        return group.copy(
            groupKey = groupKey,
            parentGroupKey = optionalContent(group.parentGroupKey)?.let { candidate ->
                normalizeMachineKey(candidate) {
                    refuseGroup(
                        "Repeatable group $groupKey names '$candidate' as its parent, which is not " +
                            "a usable group key",
                        groupKey,
                    )
                }
            },
        )
    }

    /**
     * Every group's parent chain has to reach a root rather than loop back on itself, or nothing
     * could ever say how deep one occurrence of it is nested.
     */
    private fun detectGroupNestingCycles(
        groups: List<InformationRequestTemplateGroupRequest>,
        byKey: Map<String, InformationRequestTemplateGroupRequest>,
    )
    {
        groups.forEach { group ->
            val visited = mutableSetOf(group.groupKey)
            var parentKey = group.parentGroupKey
            while (parentKey != null)
            {
                if (!visited.add(parentKey))
                {
                    refuseGroup(
                        "Repeatable group ${group.groupKey} nests inside itself through $parentKey",
                        group.groupKey,
                    )
                }
                parentKey = byKey[parentKey]?.parentGroupKey
            }
        }
    }

    /**
     * An occurrence anchor names the repeatable group a requirement is answered once per, so it has
     * to name a group this same document defines. Otherwise a requirement could claim to repeat
     * against a grouping that carries no cardinality and nests nowhere.
     */
    private fun validateOccurrenceAnchor(
        requirement: InformationRequestTemplateRequirementRequest,
        groupKeys: Set<String>,
    )
    {
        val anchor = requirement.occurrenceAnchorKey ?: return
        if (anchor !in groupKeys)
        {
            refuse(
                "Requirement ${requirement.requirementKey} is answered once per $anchor, which this " +
                    "template version does not define as a repeatable group",
                requirementKey = requirement.requirementKey,
            )
        }
    }

    // ── One section and the requirements it holds ──────────────────────────────

    private fun normalizeSection(
        section: InformationRequestTemplateSectionRequest,
    ): InformationRequestTemplateSectionRequest
    {
        val sectionKey = normalizeMachineKey(section.sectionKey) {
            refuse(machineKeyRefusal("section key", section.sectionKey), sectionKey = section.sectionKey)
        }

        if (section.requirements.isEmpty())
        {
            refuse(
                "Section $sectionKey asks for nothing, so nothing would ever render it",
                sectionKey = sectionKey,
            )
        }

        return section.copy(
            sectionKey = sectionKey,
            title = requireContent(section.title) {
                refuse("Section $sectionKey has no title", sectionKey = sectionKey)
            },
            helpText = optionalContent(section.helpText),
            requirements = section.requirements.map(::normalizeRequirement),
        )
    }

    private fun normalizeRequirement(
        requirement: InformationRequestTemplateRequirementRequest,
    ): InformationRequestTemplateRequirementRequest
    {
        val requirementKey = normalizeMachineKey(requirement.requirementKey) {
            refuse(
                machineKeyRefusal("requirement key", requirement.requirementKey),
                requirementKey = requirement.requirementKey,
            )
        }

        if (requirement.requiredness == InformationRequestRequiredness.CONDITIONAL &&
            optionalContent(requirement.conditionalRuleKey) == null
        )
        {
            refuse(
                "Requirement $requirementKey applies only sometimes but names no rule that decides it",
                requirementKey = requirementKey,
            )
        }

        val permittedDispositions = normalizeDispositions(requirementKey, requirement)
        validateAnswerability(requirementKey, requirement, permittedDispositions)
        validateCollectedField(requirementKey, requirement)

        return requirement.copy(
            requirementKey = requirementKey,
            prompt = requireContent(requirement.prompt) {
                refuse("Requirement $requirementKey states no prompt", requirementKey = requirementKey)
            },
            helpText = optionalContent(requirement.helpText),
            // Compartments and condition rules are keys into the owner's own vocabulary rather than
            // platform values, so they are only held to being present. An occurrence anchor is
            // checked further, once every group this document defines is known: see
            // validateOccurrenceAnchor.
            confidentialityCompartmentKey = optionalContent(requirement.confidentialityCompartmentKey),
            conditionalRuleKey = optionalContent(requirement.conditionalRuleKey),
            occurrenceAnchorKey = optionalContent(requirement.occurrenceAnchorKey),
            permittedDispositions = permittedDispositions,
            evidencePolicy = normalizeEvidencePolicy(requirementKey, requirement),
            substituteRequirementKeys = normalizeReferencedKeys(
                requirementKey, "substitute", requirement.substituteRequirementKeys,
            ),
            supportingEvidenceRequirementKeys = normalizeReferencedKeys(
                requirementKey, "supporting evidence", requirement.supportingEvidenceRequirementKeys,
            ),
        )
    }

    /**
     * What one version states about a requirement has to be reachable by the party it nominates.
     *
     * The response mode decides whether that party can answer at all, and every other statement
     * about answering has to agree with it. A requirement nobody can answer may still be tracked by
     * the requesting side, so the refusals are about the combination rather than about the mode.
     *
     * @param permittedDispositions the normalized set, which is what the stored rules read.
     */
    private fun validateAnswerability(
        requirementKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
        permittedDispositions: List<InformationRequestResponseDisposition>,
    )
    {
        val mode = requirement.responseMode
        if (mode !in ANSWERABLE_RESPONSE_MODES)
        {
            if (requirement.requiredness != InformationRequestRequiredness.OPTIONAL)
            {
                refuse(
                    "Requirement $requirementKey is $mode to the party it nominates, so an answer " +
                        "cannot be owed by that party",
                    requirementKey = requirementKey,
                )
            }

            if (permittedDispositions.isNotEmpty())
            {
                refuse(
                    "Requirement $requirementKey is $mode to the party it nominates, so that party " +
                        "cannot be offered answers to choose from",
                    requirementKey = requirementKey,
                )
            }

            if (requirement.evidencePolicy?.waiverPolicy ==
                InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED
            )
            {
                refuse(
                    "Requirement $requirementKey is $mode to the party it nominates, so that party " +
                        "cannot declare a waiver of it",
                    requirementKey = requirementKey,
                )
            }
        }

        // The set is an allowlist, so a set admitting nothing but a plain provided answer leaves the
        // exception the review rule waits for unreachable and the review would never happen at all.
        if (requirement.reviewPolicy == InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION &&
            permittedDispositions.all { it == InformationRequestResponseDisposition.PROVIDED }
        )
        {
            refuse(
                "Requirement $requirementKey is reviewed only when the answer is an exception, and " +
                    "it permits no answer that is one",
                requirementKey = requirementKey,
            )
        }
    }

    /**
     * The permitted answers of one requirement, as an allowlist.
     *
     * The stored waiver rule already reads the set as an allowlist, so an empty set cannot also mean
     * that everything is permitted. It means the plain provided answer and nothing else, and a
     * requirement whose nominated party can answer stores exactly that rather than leaving the
     * reading to whoever asks next. A party that cannot answer is offered nothing, so its set stays
     * empty.
     */
    private fun normalizeDispositions(
        requirementKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
    ): List<InformationRequestResponseDisposition>
    {
        val dispositions = requirement.permittedDispositions
        if (InformationRequestResponseDisposition.NOT_ANSWERED in dispositions)
        {
            refuse(
                "NOT_ANSWERED is the state a requirement starts in rather than an answer, so " +
                    "requirement $requirementKey cannot permit it",
                requirementKey = requirementKey,
            )
        }

        if (dispositions.isEmpty())
        {
            return if (requirement.responseMode in ANSWERABLE_RESPONSE_MODES)
                listOf(InformationRequestResponseDisposition.PROVIDED)
            else
                emptyList()
        }

        // Sorted so one authored set has one stored reading, whatever order it arrived in.
        return dispositions.distinct().sortedBy { it.ordinal }
    }

    /**
     * A typed answer is recorded against exactly one Field, so a requirement that asks for one names
     * it and a requirement of any other kind names none. Whether the version's own Schema Version
     * carries that Field is a separate question, asked at publication where both halves of the
     * authored document have reached storage.
     */
    private fun validateCollectedField(
        requirementKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
    )
    {
        val collected = requirement.collectedFieldDefinitionId
        if (requirement.requirementType == InformationRequestRequirementType.FIELD)
        {
            if (collected == null)
            {
                refuse(
                    "Requirement $requirementKey asks for typed data but names no field to collect " +
                        "it against",
                    requirementKey = requirementKey,
                )
            }
        }
        else if (collected != null)
        {
            refuse(
                "Requirement $requirementKey asks for ${requirement.requirementType} rather than " +
                    "typed data, so it collects no field",
                requirementKey = requirementKey,
            )
        }
    }

    /**
     * Two requirements resolving to one Field would record two answers against one attribute, and
     * nothing could later say which of them that Field holds.
     */
    private fun validateEachFieldIsCollectedOnce(
        requirements: List<InformationRequestTemplateRequirementRequest>,
    )
    {
        val seen = mutableMapOf<UUID, String>()
        requirements.forEach { requirement ->
            val collected = requirement.collectedFieldDefinitionId ?: return@forEach
            val first = seen.put(collected, requirement.requirementKey) ?: return@forEach
            refuse(
                "Requirements $first and ${requirement.requirementKey} both collect the same field, " +
                    "and one field holds one answer",
                requirementKey = requirement.requirementKey,
            )
        }
    }

    // ── The policy a requested document is judged by ───────────────────────────

    private fun normalizeEvidencePolicy(
        requirementKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
    ): InformationRequestTemplateEvidencePolicyRequest?
    {
        val policy = requirement.evidencePolicy ?: return null
        if (requirement.requirementType != InformationRequestRequirementType.DOCUMENT)
        {
            refuse(
                "An evidence policy belongs to a requested document, and requirement " +
                    "$requirementKey asks for ${requirement.requirementType}",
                requirementKey = requirementKey,
            )
        }

        return policy.copy(
            acceptedValues = normalizeAcceptedValues(requirementKey, policy),
        )
    }

    private fun normalizeAcceptedValues(
        requirementKey: String,
        policy: InformationRequestTemplateEvidencePolicyRequest,
    ): List<InformationRequestTemplateAcceptedValueRequest>
    {
        return policy.acceptedValues
            .map { accepted ->
                accepted.copy(
                    acceptedValue = requireContent(accepted.acceptedValue) {
                        refuse(
                            "Requirement $requirementKey restricts ${accepted.attribute} to an " +
                                "empty value",
                            requirementKey = requirementKey,
                        )
                    },
                )
            }
            .distinct()
            .onEach { accepted ->
                val capture = capturedRequirementFor(policy, accepted.attribute) ?: return@onEach
                if (capture == InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED)
                {
                    refuse(
                        "Requirement $requirementKey never captures ${accepted.attribute}, so it " +
                            "cannot restrict which values of it are accepted",
                        requirementKey = requirementKey,
                    )
                }
            }
    }

    /**
     * Which stated attribute an accepted value restricts, or null when the attribute is read from
     * the file rather than stated about it and so restricting it depends on nothing.
     */
    private fun capturedRequirementFor(
        policy: InformationRequestTemplateEvidencePolicyRequest,
        attribute: InformationRequestEvidenceAttribute,
    ): InformationRequestEvidenceAttributeRequirement? = when (attribute)
    {
        InformationRequestEvidenceAttribute.CONTENT_TYPE -> null
        InformationRequestEvidenceAttribute.ISSUER -> policy.issuerRequirement
        InformationRequestEvidenceAttribute.JURISDICTION -> policy.jurisdictionRequirement
        InformationRequestEvidenceAttribute.LANGUAGE -> policy.languageRequirement
    }

    // ── Relations between two requirements of one document ─────────────────────

    private fun validateRelations(
        requirement: InformationRequestTemplateRequirementRequest,
        typeByKey: Map<String, InformationRequestRequirementType>,
    )
    {
        val key = requirement.requirementKey
        val asksForADocument = requirement.requirementType == InformationRequestRequirementType.DOCUMENT

        if (requirement.substituteRequirementKeys.isNotEmpty() && !asksForADocument)
        {
            refuse(
                "Only a requested document declares substitute evidence, and requirement $key asks " +
                    "for ${requirement.requirementType}",
                requirementKey = key,
            )
        }

        if (requirement.supportingEvidenceRequirementKeys.isNotEmpty() && asksForADocument)
        {
            refuse(
                "A requested document does not itself declare supporting evidence, and requirement " +
                    "$key does",
                requirementKey = key,
            )
        }

        val related = requirement.substituteRequirementKeys + requirement.supportingEvidenceRequirementKeys
        related.forEach { referenced ->
            if (referenced == key)
            {
                refuse(
                    "Requirement $key references itself, and nothing stands in for or supports itself",
                    requirementKey = key,
                )
            }

            val referencedType = typeByKey[referenced]
                ?: refuse(
                    "Requirement $key references $referenced, which this template version does not " +
                        "ask for",
                    requirementKey = referenced,
                )
            if (referencedType != InformationRequestRequirementType.DOCUMENT)
            {
                refuse(
                    "Requirement $key references $referenced, which asks for $referencedType rather " +
                        "than a document",
                    requirementKey = referenced,
                )
            }
        }
    }

    /**
     * Both ends of a chain are refused, so the alternatives for one document are exactly what it
     * names and resolving them never has to follow a second hop.
     */
    private fun validateSubstitutionIsFlat(requirements: List<InformationRequestTemplateRequirementRequest>)
    {
        val namedAsSubstitute = requirements.flatMapTo(mutableSetOf()) { it.substituteRequirementKeys }
        requirements.forEach { requirement ->
            if (requirement.substituteRequirementKeys.isNotEmpty() &&
                requirement.requirementKey in namedAsSubstitute
            )
            {
                refuse(
                    "Requirement ${requirement.requirementKey} both stands in for another document " +
                        "and declares its own substitutes, which would chain substitution",
                    requirementKey = requirement.requirementKey,
                )
            }
        }
    }

    // ── Shared normalization ──────────────────────────────────────────────────

    private fun normalizeReferencedKeys(
        requirementKey: String,
        relation: String,
        keys: List<String>,
    ): List<String> = keys
        .map { candidate ->
            normalizeMachineKey(candidate) {
                refuse(
                    "Requirement $requirementKey names '$candidate' as $relation, which is not a " +
                        "usable requirement key",
                    requirementKey = requirementKey,
                )
            }
        }
        .distinct()

    private fun normalizeMachineKey(candidate: String, onInvalid: () -> Nothing): String =
        InformationRequestTemplateKey.normalizeOrNull(candidate) ?: onInvalid()

    private fun machineKeyRefusal(label: String, candidate: String): String =
        InformationRequestTemplateKey.refusalFor(label, candidate)

    private fun requireContent(candidate: String, onBlank: () -> Nothing): String =
        candidate.trim().ifBlank { onBlank() }

    private fun optionalContent(candidate: String?): String? = candidate?.trim()?.ifBlank { null }

    private fun requireDistinct(keys: List<String>, onRepeat: (String) -> Nothing)
    {
        val seen = mutableSetOf<String>()
        keys.forEach { key ->
            if (!seen.add(key))
            {
                onRepeat(key)
            }
        }
    }

    private fun refuse(
        message: String,
        sectionKey: String? = null,
        requirementKey: String? = null,
    ): Nothing = throw InformationRequestTemplateValidationException(message, sectionKey, requirementKey)

    private fun refuseGroup(message: String, groupKey: String): Nothing =
        throw InformationRequestTemplateValidationException(message, groupKey = groupKey)

    private fun refuseConditionRule(message: String, ruleKey: String): Nothing =
        throw InformationRequestTemplateValidationException(message, groupKey = ruleKey)

    private companion object
    {
        val ANSWERABLE_RESPONSE_MODES = setOf(
            InformationRequestResponseMode.PROVIDE,
            InformationRequestResponseMode.PROVIDE_ONCE,
        )
        const val SUPPORTED_CONDITION_EXPRESSION_VERSION = 1
        val VALUELESS_CONDITION_OPERATORS = setOf(
            FieldOperator.IS_EMPTY,
            FieldOperator.IS_NOT_EMPTY,
        )
        val DISPOSITION_OPERATORS = setOf(
            FieldOperator.EQUALS,
            FieldOperator.NOT_EQUALS,
            FieldOperator.IS_EMPTY,
            FieldOperator.IS_NOT_EMPTY,
        )
        val DISPOSITION_VALUE_OPERATORS = setOf(
            FieldOperator.EQUALS,
            FieldOperator.NOT_EQUALS,
        )
    }
}
