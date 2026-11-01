package com.docuhyphen.app.api.service.workflow

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class WorkflowEscalationScheduler
{
    private val logger = LoggerFactory.getLogger(WorkflowEscalationScheduler::class.java)

    @Inject
    private lateinit var engine: WorkflowEngineService

    @Scheduled(
        every = "\${app.workflow.escalation.every:60s}",
        identity = "workflow-escalation",
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

