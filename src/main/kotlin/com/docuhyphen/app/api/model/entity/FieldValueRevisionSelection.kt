package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.util.UUID

/**
 * A canonical selected option code belonging to one [FieldValueRevision]. Chosen options are part of
 * the answer a revision records, so they are kept with the revision rather than read back from the
 * live answer, whose selections a later change replaces.
 */
@Entity
@Table(name = "field_value_revision_selection")
class FieldValueRevisionSelection
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "field_value_revision_id", nullable = false)
    lateinit var fieldValueRevisionId: UUID

    @Column(name = "option_code", nullable = false, length = 128)
    lateinit var optionCode: String

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    constructor()
}
