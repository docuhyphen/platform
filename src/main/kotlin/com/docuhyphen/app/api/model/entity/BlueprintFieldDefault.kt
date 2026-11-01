package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.util.*

@Entity
@Serializable
@Table(name = "blueprint_field_default")
class BlueprintFieldDefault
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "blueprint_definition_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var blueprintDefinitionId: UUID

    @Column(name = "field_definition_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var fieldDefinitionId: UUID

    @Column(name = "value_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var valueType: FieldValueType

    @Column(name = "value_json", nullable = true, columnDefinition = "text")
    var valueJson: String? = null

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    constructor()
}
