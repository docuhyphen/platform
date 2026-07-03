package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.util.UUID

/**
 * A canonical selected option code for a SINGLE_SELECT (one row) or MULTI_SELECT (many rows)
 * [FieldValue]. Stores stable option codes, never mutable display labels.
 * See FIELDS-FEATURE.md "Option Set and Option".
 */
@Entity
@Table(name = "field_value_selection")
class FieldValueSelection
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "field_value_id", nullable = false)
    lateinit var fieldValueId: UUID

    @Column(name = "option_code", nullable = false, length = 128)
    lateinit var optionCode: String

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    constructor()
}
