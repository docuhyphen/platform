package com.docuhyphen.app.api.service.informationrequest.template

internal object InformationRequestTemplateConfigurationChecks {
    fun normalizeMachineKey(candidate: String, onInvalid: () -> Nothing): String =
        InformationRequestTemplateKey.normalizeOrNull(candidate) ?: onInvalid()

    fun machineKeyRefusal(label: String, candidate: String): String =
        InformationRequestTemplateKey.refusalFor(label, candidate)

    fun requireContent(candidate: String, onBlank: () -> Nothing): String =
        candidate.trim().ifBlank { onBlank() }

    fun optionalContent(candidate: String?): String? = candidate?.trim()?.ifBlank { null }

    fun requireDistinct(keys: List<String>, onRepeat: (String) -> Nothing) {
        val seen = mutableSetOf<String>()
        keys.forEach { key ->
            if (!seen.add(key)) {
                onRepeat(key)
            }
        }
    }

    fun refuse(
        message: String,
        sectionKey: String? = null,
        requirementKey: String? = null,
    ): Nothing = throw InformationRequestTemplateValidationException(message, sectionKey, requirementKey)

    fun refuseGroup(message: String, groupKey: String): Nothing =
        throw InformationRequestTemplateValidationException(message, groupKey = groupKey)

    fun refuseConditionRule(message: String, ruleKey: String): Nothing =
        throw InformationRequestTemplateValidationException(message, conditionRuleKey = ruleKey)
}
