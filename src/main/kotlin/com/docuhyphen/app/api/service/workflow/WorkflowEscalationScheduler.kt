package com.docuhyphen.app.api.service.workflow

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

/**
 * Periodically nudges the [WorkflowEngineService] to escalate pending steps whose SLA
 * has elapsed. Runs once every minute by default; tune with the config key
 * `app.workflow.escalation.every`.
 *
 * Quarkus' built-in scheduler bean is wired automatically when `quarkus-scheduler` is on
 * the classpath — which it is transitively via `quarkus-arc`. No extra dependency is
 * needed.
 *
 * The job is deliberately tiny: all logic stays in the engine so it can also be invoked
 * synchronously from tests or admin actions.
 */
@ApplicationScoped
class WorkflowEscalationScheduler
{
    private val logger = LoggerFactory.getLogger(WorkflowEscalationScheduler::class.java)

    @Inject private lateinit var engine: WorkflowEngineService

    @Scheduled(
        every = "\${app.workflow.escalation.every:60s}",
        identity = "workflow-escalation",
        // Skip overlapping fires; one in-flight escalation pass at a time is plenty.
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    fun tick()
    {
        try
        {
            val now = Timestamp.from(Instant.now())
            val escalated = engine.escalateOverdue(now)
            if (escalated > 0)
            {
                logger.info("Escalation tick: {} step(s) escalated", escalated)
            }
        }
        catch (t: Throwable)
        {
            logger.error("Escalation tick failed", t)
        }
    }
}

