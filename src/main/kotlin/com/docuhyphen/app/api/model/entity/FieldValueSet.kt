package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * A named set of typed answers belonging to one [SchemaAssignment]. The [FieldValueSetKind.ROOT] set
 * holds the answers the assigned resource gives as itself and is the set every existing resource
 * projection reads. A [FieldValueSetKind.OCCURRENCE] set holds one repetition of a repeatable group
 * and is addressed by its [occurrencePath] within the assignment, which is what allows the same
 * question to be answered once per repetition.
 */
@Entity
@Table(name = "field_value_set")
class FieldValueSet
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "schema_assignment_id", nullable = false)
    lateinit var schemaAssignmentId: UUID

    @Column(name = "set_kind", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var setKind: FieldValueSetKind = FieldValueSetKind.ROOT

    /** Null for a root set; the stable path of the repetition for an occurrence set. */
    @Column(name = "occurrence_path", nullable = true, length = 512)
    var occurrencePath: String? = null

    /**
     * How many recorded changes this set has been through, starting at its first state. It only
     * ever moves forward, which is what lets a client name the exact state of the set it edited.
     */
    @Column(name = "revision", nullable = false)
    var revision: Long = 1

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
