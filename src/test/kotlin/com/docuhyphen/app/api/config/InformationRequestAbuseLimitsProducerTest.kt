package com.docuhyphen.app.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class InformationRequestAbuseLimitsProducerTest {
    @Test
    fun `the configured defaults produce the limits`() {
        val limits = producer().abuseLimits()

        assertEquals(25, limits.accessLinkUses)
        assertEquals(Duration.ofDays(30), limits.accessLinkLifetime)
    }

    @Test
    fun `a zero or negative limit is refused when the limits are produced`() {
        listOf(
            producer(challenges = 0) to "app.information-request.no-auth.challenges-per-minute",
            producer(sessions = -1) to "app.information-request.no-auth.sessions-per-minute",
            producer(lifetime = Duration.ZERO) to "app.information-request.access-link.default-lifetime",
            producer(uses = 0) to "app.information-request.access-link.default-uses",
            producer(cooldown = Duration.ofSeconds(-1)) to "app.information-request.reminder.cooldown",
            producer(exports = 0) to "app.information-request.export.daily-ceiling",
        ).forEach { (configured, property) ->
            val refusal = assertThrows<IllegalArgumentException> { configured.abuseLimits() }
            assertTrue(refusal.message.orEmpty().contains(property), refusal.message)
        }
    }

    private fun producer(
        challenges: Long = 10,
        sessions: Long = 20,
        lifetime: Duration = Duration.ofDays(30),
        uses: Int = 25,
        cooldown: Duration = Duration.ofHours(24),
        exports: Long = 100,
    ) = InformationRequestAbuseLimitsProducer(challenges, sessions, lifetime, uses, cooldown, exports)
}
