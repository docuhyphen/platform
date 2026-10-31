package com.docuhyphen.app.api.service.informationrequest.privacy.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import java.util.*

internal data class AttributedPrivacyRecord(
    val actorKind: String,
    val actorLabel: String?,
    val sessionId: String?,
    val actorId: UUID?
)

internal data class PrivacyFixture(val runtime: SubmissionRuntimeSqlFixture, val subjectId: UUID) {
    val recordOwner: RecordOwnerRef get() = RecordOwnerRef.organization(runtime.template.organizationId)
    val principal: PrincipalRef get() = PrincipalRef.user(runtime.template.userId)
}
