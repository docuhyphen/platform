package com.docuhyphen.app.api.service.informationrequest.party.model

import java.util.*

internal data class PartyConcurrencyFixture(
    val organizationId: UUID = UUID.randomUUID(),
    val userId: UUID = UUID.randomUUID(),
    val exchangeId: UUID = UUID.randomUUID(),
    val definitionId: UUID = UUID.randomUUID(),
    val versionId: UUID = UUID.randomUUID(),
    val requestId: UUID = UUID.randomUUID(),
    val partyId: UUID = UUID.randomUUID(),
    val requirementId: UUID = UUID.randomUUID(),
)
