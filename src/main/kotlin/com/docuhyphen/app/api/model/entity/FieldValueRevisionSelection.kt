package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

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
