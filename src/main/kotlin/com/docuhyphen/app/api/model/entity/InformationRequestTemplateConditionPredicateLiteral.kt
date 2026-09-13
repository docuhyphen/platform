package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One literal an `IN` / `NOT_IN` [InformationRequestTemplateConditionPredicate] compares a
 * SINGLE_SELECT or MULTI_SELECT answer against. Ordered the way it was authored.
 */
@Entity
@Table(name = "information_request_template_condition_predicate_literal")
class InformationRequestTemplateConditionPredicateLiteral
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "condition_predicate_id", nullable = false)
    lateinit var conditionPredicateId: UUID

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "literal_value", nullable = false, length = 256)
    lateinit var literalValue: String

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    constructor()
}
