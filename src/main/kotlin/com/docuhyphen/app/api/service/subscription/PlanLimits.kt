package com.docuhyphen.app.api.service.subscription

/**
 * Quantitative allowances attached to a plan. A null value means the allowance is not capped
 * commercially. Security rate limits, file-size limits, and abuse controls still apply
 * independently of these values.
 *
 * @param maxNewExchangesPerCalendarMonth new Exchanges the owner may create in a UTC calendar month.
 * @param maxOpenExchanges owned Exchanges that may sit in an open state at the same time.
 * @param maxAdditionalParticipantsPerExchange participants beyond the required primary recipient.
 * @param includedSeats seats the plan grants without a separate seat purchase.
 * @param seatsArePurchased true when seat capacity comes from a purchased quantity rather than the plan.
 */
data class PlanLimits(
    val maxNewExchangesPerCalendarMonth: Long?,
    val maxOpenExchanges: Long?,
    val maxAdditionalParticipantsPerExchange: Long?,
    val includedSeats: Long?,
    val seatsArePurchased: Boolean,
)
{
    val hasExchangeCreationCap: Boolean
        get() = maxNewExchangesPerCalendarMonth != null

    val hasOpenExchangeCap: Boolean
        get() = maxOpenExchanges != null
}

