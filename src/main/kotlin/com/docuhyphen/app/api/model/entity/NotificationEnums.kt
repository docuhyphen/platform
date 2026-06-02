package com.docuhyphen.app.api.model.entity

/** Delivery channels a notification can be routed to. */
enum class NotificationChannelType
{
    EMAIL,
    IN_APP,
    SMS,
    SLACK,
    TEAMS,
    WHATSAPP,
}

/** Whether the user receives notifications immediately or as a batched digest. */
enum class NotificationDelivery
{
    INSTANT,
    DIGEST_HOURLY,
    DIGEST_DAILY,
}

/** Outcome of a single delivery attempt (recorded in `notification_delivery_log`). */
enum class NotificationDeliveryOutcome
{
    DELIVERED,
    FAILED,
    SUPPRESSED,
    QUIET_HOURS,
    FALLBACK,
    SKIPPED,
}

/** Whether a rule notifies its assignees or explicitly suppresses delivery for matches. */
enum class NotificationRuleAction
{
    NOTIFY,
    SUPPRESS,
}

/** Scope of a [NotificationRule]: application-wide template or per-organisation. */
enum class NotificationRuleScope
{
    APP,
    ORG,
}

