package com.docuhyphen.app.api.model.informationrequest.party

import com.docuhyphen.app.api.model.entity.SubjectKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.util.*

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
