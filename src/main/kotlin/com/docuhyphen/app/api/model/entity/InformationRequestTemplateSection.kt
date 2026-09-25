package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One ordered grouping of requirements within a single [InformationRequestTemplateVersion]. The
 * section key recognises the same grouping across Versions, while the position is a property of
 * this Version alone, so a later Version may reorder without renaming.
 */
@Entity
@Table(name = "information_request_template_section")
class InformationRequestTemplateSection
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "section_key", nullable = false, length = 128)
    lateinit var sectionKey: String

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 1

    @Column(name = "title", nullable = false, length = 255)
    lateinit var title: String

    @Column(name = "help_text", nullable = true, length = 2048)
    var helpText: String? = null

    @Column(name = "submission_stage_key", nullable = true, length = 128)
    var submissionStageKey: String? = null

    constructor()
}
