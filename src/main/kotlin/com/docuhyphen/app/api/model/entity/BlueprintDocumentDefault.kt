package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.util.*

/**
 * One document default attached to a [BlueprintDefinition]. Replaces the
 * `exchangeDocuments[]` array formerly embedded in `config_json`. `libraryDocumentId` is a
 * real foreign key to document_library (ON DELETE SET NULL) so a deleted library document
 * no longer leaves a dangling reference inside a JSON blob.
 */
@Entity
@Serializable
@Table(name = "blueprint_document_default")
class BlueprintDocumentDefault
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "blueprint_definition_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var blueprintDefinitionId: UUID

    @Column(name = "title", nullable = false)
    lateinit var title: String

    @Column(name = "restricted_type", nullable = true, length = 16)
    var restrictedType: String? = null

    @Column(name = "restrict_type", nullable = false)
    var restrictType: Boolean = false

    @Column(name = "required", nullable = false)
    var required: Boolean = false

    @Column(name = "library_document_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var libraryDocumentId: UUID? = null

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    constructor()
}
