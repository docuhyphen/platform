package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.SubjectKind
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import java.util.UUID

data class InformationRequestSubjectReference(
    val authority: String,
    val identifierType: String,
    val identifierValue: String,
)

data class AssignInformationRequestSubjectCommand(
    val requestId: UUID,
    val subjectKind: SubjectKind,
    val reference: InformationRequestSubjectReference?,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)
