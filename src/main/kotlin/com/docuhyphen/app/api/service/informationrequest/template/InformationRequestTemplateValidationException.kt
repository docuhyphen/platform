package com.docuhyphen.app.api.service.informationrequest.template

class InformationRequestTemplateValidationException(
    override val message: String,
    val sectionKey: String? = null,
    val requirementKey: String? = null,
    val groupKey: String? = null,
    val reviewStageKey: String? = null,
    val conditionRuleKey: String? = null,
) : RuntimeException(message)
