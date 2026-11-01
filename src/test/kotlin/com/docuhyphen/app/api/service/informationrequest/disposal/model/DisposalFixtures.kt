package com.docuhyphen.app.api.service.informationrequest.disposal.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

internal data class SubmissionFixtureRef(
    val requestId: UUID,
    val exchangeId: UUID,
    val templateVersionId: UUID,
    val documentId: UUID,
    val documentVersionId: UUID,
    val owner: RecordOwnerRef,
    val principal: PrincipalRef,
) {
    companion object {
        fun of(runtime: SubmissionRuntimeSqlFixture) = SubmissionFixtureRef(
            requestId = runtime.requestId,
            exchangeId = runtime.exchangeId,
            templateVersionId = runtime.template.versionId,
            documentId = runtime.documentId,
            documentVersionId = runtime.documentVersionId,
            owner = RecordOwnerRef.organization(runtime.template.organizationId),
            principal = PrincipalRef.user(runtime.template.userId),
        )
    }
}

internal data class SharedSubjectExport(
    val source: SubmissionFixtureRef,
    val sibling: SubmissionFixtureRef,
    val exportId: UUID,
)

internal data class SharedExportWithRecords(
    val source: SubmissionFixtureRef,
    val sibling: SubmissionFixtureRef,
    val siblingRecords: Map<String, UUID>,
    val exportId: UUID,
)

internal data class SubjectSources(
    val source: SubmissionFixtureRef,
    val sibling: SubmissionFixtureRef,
    val subjectId: UUID,
    val siblingSubmittedEvidenceVersionId: UUID,
)
