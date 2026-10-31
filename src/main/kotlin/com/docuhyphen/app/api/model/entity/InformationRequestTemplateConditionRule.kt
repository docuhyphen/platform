package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "information_request_template_condition_rule")
class InformationRequestTemplateConditionRule
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "rule_key", nullable = false, length = 128)
    lateinit var ruleKey: String

    @Column(name = "expression_version", nullable = false)
    var expressionVersion: Int = 1

    @Column(name = "hidden_data_policy", nullable = false, length = 48)
    @Enumerated(EnumType.STRING)
    var hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy =
        InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY

    constructor()
}
