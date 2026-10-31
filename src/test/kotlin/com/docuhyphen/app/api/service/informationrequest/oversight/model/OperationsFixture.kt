package com.docuhyphen.app.api.service.informationrequest.oversight.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import java.util.*

internal data class OperationsFixture(
    val runtime: SubmissionRuntimeSqlFixture,
    val owner: InformationRequestOwnerRef,
    val userId: UUID,
    val contributorUserId: UUID,
    val attestorUserId: UUID,
    val groupPartyId: UUID,
    val overdueId: UUID,
    val dueSoonId: UUID,
    val quietId: UUID,
)
