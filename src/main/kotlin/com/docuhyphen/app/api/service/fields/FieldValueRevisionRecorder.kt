package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.model.entity.FieldValueRevisionSelection
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue
import com.docuhyphen.app.api.model.fields.FieldPrincipalProvenance
import com.docuhyphen.app.api.repository.fields.FieldValueRevisionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRevisionSelectionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class FieldValueRevisionRecorder @Inject constructor(
    private val revisionRepository: FieldValueRevisionRepository,
    private val revisionSelectionRepository: FieldValueRevisionSelectionRepository,
)
{
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
