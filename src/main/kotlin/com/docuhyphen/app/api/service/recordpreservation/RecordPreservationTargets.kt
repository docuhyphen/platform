package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.exception.RecordPreservationNotFoundException
import com.docuhyphen.app.api.exception.RecordPreservationRequestException
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.recordpreservation.RecordPreservationTargetRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class RecordPreservationTargets @Inject constructor(
    private val targetRepository: RecordPreservationTargetRepository,
) {
    fun requireHoldable(owner: RecordOwnerRef, resourceType: String, resourceId: String) {
        val type = resourceType.trim().uppercase()
        if (type !in RecordPreservationResourceTypes.HOLDABLE) {
            throw RecordPreservationRequestException("Records of type $type cannot be placed on hold")
        }
        val id = runCatching { UUID.fromString(resourceId.trim()) }.getOrNull()
        if (id == null || !targetRepository.belongsTo(owner, type, id)) {
            throw RecordPreservationNotFoundException("Record not found")
        }
    }
}
