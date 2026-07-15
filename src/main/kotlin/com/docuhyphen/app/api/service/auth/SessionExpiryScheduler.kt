package com.docuhyphen.app.api.service.auth

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Periodically revokes sessions that have passed their absolute expiry without an explicit
 * sign-out. This covers the "browser closed" case: no further requests arrive, so the
 * EndpointAuthorizationFilter never fires to enforce the expiry. Without this job, expired
 * sessions would silently disappear from listActiveSessions (filtered by expiresAt) but peer
 * devices would never receive SESSION_REMOVED and their sessions-tab lists would go stale.
 */
@ApplicationScoped
class SessionExpiryScheduler
{
    private val logger = LoggerFactory.getLogger(SessionExpiryScheduler::class.java)

    @Inject private lateinit var userSessionService: UserSessionService

    @Scheduled(
        every = "\${app.auth.session-expiry.cleanup-interval:5m}",
        identity = "session-expiry-cleanup",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    fun tick()
    {
        try
        {
            val expired = userSessionService.cleanupExpiredSessions()
            val idleTimedOut = userSessionService.cleanupIdleTimedOutSessions()
            if (expired > 0)
            {
                logger.info("Session expiry cleanup: {} session(s) expired and removed", expired)
            }
            if (idleTimedOut > 0)
            {
                logger.info("Idle session cleanup: {} session(s) revoked after timeout", idleTimedOut)
            }
        }
        catch (t: Throwable)
        {
            logger.error("Session expiry cleanup tick failed", t)
        }
    }
}
