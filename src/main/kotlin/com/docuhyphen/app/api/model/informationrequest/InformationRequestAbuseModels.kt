package com.docuhyphen.app.api.model.informationrequest

import java.time.Duration
import java.time.Instant

enum class InformationRequestAbuseControl
{
    NO_AUTH_CHALLENGE_RATE,
    NO_AUTH_SESSION_RATE,
    REMINDER_COOLDOWN,
    EXPORT_DAILY_CEILING,
    EVIDENCE_UPLOAD_LIMIT,
}

enum class InformationRequestNoAuthAttempt
{
    CHALLENGE,
    SESSION,
}

data class InformationRequestAbuseLimits(
    val noAuthChallengesPerMinute: Long = 10,
    val noAuthSessionsPerMinute: Long = 20,
    val accessLinkLifetime: Duration = Duration.ofDays(30),
    val accessLinkUses: Int = 25,
    val reminderCooldown: Duration = Duration.ofHours(24),
    val exportDailyCeiling: Long = 100,
)

data class InformationRequestExportWindow(
    val count: Long,
    val oldestRequestedAt: Instant?,
)
