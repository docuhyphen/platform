package com.docuhyphen.app.api.service.informationrequest.model

import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import java.util.*

internal data class AuthorizationOrderSubmission(val fixture: SubmissionRuntimeSqlFixture, val packageId: UUID)
