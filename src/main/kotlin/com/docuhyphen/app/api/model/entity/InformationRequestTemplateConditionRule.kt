package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One versioned condition an [InformationRequestTemplateVersion] defines. A requirement binding
 * that names this rule's key as its conditional rule is asked only while the rule evaluates true.
 *
 * Rules are structure one version states, not a stable identity: a rewrite of the document
 * replaces every rule of the version the same way it replaces every section, binding, and group.
 */
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
