package com.docuhyphen.app.api.service.informationrequest.conformance.model

import java.util.*

internal data class AmendedHiddenAnswerFixture(val request: PublishedRequest, val schemaVersionId: UUID)

internal data class AmendedHiddenStoredAnswer(val active: Boolean, val hiddenByRule: String?, val narrative: String?)
