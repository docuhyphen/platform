package com.docuhyphen.app.api.model.entity

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationChannelType
{
    EMAIL,
    IN_APP,
    SMS,
    SLACK,
    TEAMS,
    WHATSAPP,
}

enum class NotificationDelivery
{
    INSTANT,
    DIGEST_HOURLY,
    DIGEST_DAILY,
}

enum class NotificationDeliveryOutcome
{
    DELIVERED,
    FAILED,
    SUPPRESSED,
    QUIET_HOURS,
    FALLBACK,
    SKIPPED,
}

enum class NotificationRuleAction
{
    NOTIFY,
    SUPPRESS,
}

enum class NotificationRuleScope
{
    APP,
    ORG,
}

