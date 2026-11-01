package com.docuhyphen.app.api.model.subscription

data class PlanLimits(
    val maxNewExchangesPerCalendarMonth: Long?,
    val maxOpenExchanges: Long?,
    val maxAdditionalParticipantsPerExchange: Long?,
    val includedSeats: Long?,
    val seatsArePurchased: Boolean,
    val maxOpenInformationRequests: Long? = null,
    val maxActingPartiesPerInformationRequest: Long? = null,
    val maxEvidenceFilesPerInformationRequest: Long? = null,
    val maxEvidenceBytesPerInformationRequest: Long? = null,
    val maxCommittedEvidenceBytes: Long? = null,
)
{
    val hasExchangeCreationCap: Boolean
        get() = maxNewExchangesPerCalendarMonth != null

    val hasOpenExchangeCap: Boolean
        get() = maxOpenExchanges != null
}

