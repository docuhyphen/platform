package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "information_request_template_requirement_group")
class InformationRequestTemplateRequirementGroup
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "group_key", nullable = false, length = 128)
    lateinit var groupKey: String

    @Column(name = "parent_group_id", nullable = true)
    var parentGroupId: UUID? = null

    @Column(name = "min_occurrences", nullable = false)
    var minOccurrences: Int = 0

    @Column(name = "max_occurrences", nullable = true)
    var maxOccurrences: Int? = null

    constructor()
}
