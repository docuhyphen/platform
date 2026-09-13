package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One repeatable or nested grouping an [InformationRequestTemplateVersion] defines. A requirement
 * binding that names this group's key as its occurrence anchor is answered once per runtime
 * occurrence of the group. [parentGroupId] nests one group's occurrences inside another's, null
 * meaning the group repeats directly under the request.
 *
 * Groups are structure one version states, not a stable identity: a rewrite of the document
 * replaces every group of the version the same way it replaces every section and binding.
 */
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
