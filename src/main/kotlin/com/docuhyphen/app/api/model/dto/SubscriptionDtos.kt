package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The commercial position of the paying subject for the current context, returned on the
 * session so the app can present plan availability and usage without guessing.
 *
 * The API remains the authority: this contract exists to keep the app from leading a customer
 * into a flow the API will refuse, not to replace the server-side check.
 *
 * Instant-valued fields are ISO-8601 UTC strings.
 */
@Serializable
data class EffectiveSubscriptionDto(
    val planCode: String,
    val ownerType: String,
    @Serializable(with = UUIDSerializer::class)
    val ownerId: UUID,
    val status: String,
    val features: List<String>,
    val limits: SubscriptionLimitsDto,
    val usage: SubscriptionUsageDto,
    val allowsMutations: Boolean,
    val billingFrequency: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val gracePeriodEnd: String? = null,
    val upgradePlanCode: String? = null,
    val enforcementMode: String,
)

/**
 * Allowances in force for the resolved plan. A null value means the allowance is not capped
 * commercially; security rate limits and abuse controls still apply independently.
 */
@Serializable
data class SubscriptionLimitsDto(
    val maxNewExchangesPerCalendarMonth: Long? = null,
    val maxOpenExchanges: Long? = null,
    val maxAdditionalParticipantsPerExchange: Long? = null,
    val seatCapacity: Long? = null,
    val seatsArePurchased: Boolean,
)

/**
 * How much of each capped allowance is currently consumed. A field is null when the resolved
 * plan does not cap that allowance, so nothing is counted that nobody is limited by.
 */
@Serializable
data class SubscriptionUsageDto(
    val newExchangesThisPeriod: Long? = null,
    val openExchanges: Long? = null,
    val activeSeats: Long? = null,
    val usagePeriodStart: String? = null,
    val usagePeriodEnd: String? = null,
)

/**
 * Structured body returned when a commercial plan check refuses an operation. [errorMessage]
 * keeps the shape existing clients already read for failures, and the remaining fields let the
 * app explain the allowance and offer the plan that lifts it.
 */
@Serializable
data class SubscriptionDenialDto(
    val errorMessage: String,
    val reasonCode: String,
    val planCode: String,
    val featureCode: String? = null,
    val currentValue: Long? = null,
    val limit: Long? = null,
    val upgradePlanCode: String? = null,
)

