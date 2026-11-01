package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

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

@Serializable
data class SubscriptionLimitsDto(
    val maxNewExchangesPerCalendarMonth: Long? = null,
    val maxOpenExchanges: Long? = null,
    val maxAdditionalParticipantsPerExchange: Long? = null,
    val seatCapacity: Long? = null,
    val seatsArePurchased: Boolean,
    val maxOpenInformationRequests: Long? = null,
    val maxActingPartiesPerInformationRequest: Long? = null,
    val maxEvidenceFilesPerInformationRequest: Long? = null,
    val maxEvidenceBytesPerInformationRequest: Long? = null,
    val maxCommittedEvidenceBytes: Long? = null,
)

@Serializable
data class SubscriptionUsageDto(
    val newExchangesThisPeriod: Long? = null,
    val openExchanges: Long? = null,
    val activeSeats: Long? = null,
    val usagePeriodStart: String? = null,
    val usagePeriodEnd: String? = null,
)

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

