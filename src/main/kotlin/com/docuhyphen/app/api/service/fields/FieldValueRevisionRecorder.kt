package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.model.entity.FieldValueRevisionSelection
import com.docuhyphen.app.api.repository.fields.FieldValueRevisionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRevisionSelectionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Appends the immutable history of a typed answer. Each recorded change becomes one
 * [FieldValueRevision] numbered within its Value Set and question, so a repetition of a group
 * numbers its own answers independently and any later record can name the exact answer it relied on.
 *
 * Nothing here rewrites or removes an earlier revision. Clearing an answer is recorded as a revision
 * that holds no value rather than as the absence of one.
 */
@ApplicationScoped
class FieldValueRevisionRecorder @Inject constructor(
    private val revisionRepository: FieldValueRevisionRepository,
    private val revisionSelectionRepository: FieldValueRevisionSelectionRepository,
)
{
    /**
     * Records [value] as it now stands, together with the [canonical] state that produced it and the
     * [provenance] of the principal that made the change.
     */
    fun record(
        value: FieldValue,
        canonical: CanonicalFieldValue,
        provenance: FieldPrincipalProvenance,
    ): FieldValueRevision
    {
        val revision = FieldValueRevision().apply {
            this.fieldValueId = value.id
            this.fieldValueSetId = value.fieldValueSetId
            this.schemaAssignmentId = value.schemaAssignmentId
            this.schemaFieldBindingId = value.schemaFieldBindingId
            this.fieldContractId = value.fieldContractId
            this.revisionNumber = nextRevisionNumber(value.fieldValueSetId, value.fieldContractId)
            this.valueType = value.valueType
            this.textValue = value.textValue
            this.numberValue = value.numberValue
            this.boolValue = value.boolValue
            this.dateValue = value.dateValue
            this.datetimeValue = value.datetimeValue
            this.datetimeOffsetMinutes = value.datetimeOffsetMinutes
            this.isCleared = canonical.isEmpty
            this.provenance = value.provenance
            this.recordedAt = Timestamp.from(Instant.now())
        }
        provenance.recordOn(revision)
        val saved = revisionRepository.save(revision)

        canonical.selectionCodes.forEachIndexed { index, code ->
            revisionSelectionRepository.save(
                FieldValueRevisionSelection().apply {
                    this.fieldValueRevisionId = saved.id
                    this.optionCode = code
                    this.displayOrder = index
                },
            )
        }
        return saved
    }

    private fun nextRevisionNumber(fieldValueSetId: UUID, fieldContractId: UUID): Int =
        (revisionRepository.findLatest(fieldValueSetId, fieldContractId)?.revisionNumber ?: 0) + 1
}
