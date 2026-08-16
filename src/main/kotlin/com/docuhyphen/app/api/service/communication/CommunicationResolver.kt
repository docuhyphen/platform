package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.repository.communication.CommunicationRepository
import com.docuhyphen.app.api.service.variable.TemplateVariableInterpolator
import com.docuhyphen.app.api.service.variable.VariableResolutionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Resolves a [Communication] at workflow execution time: looks up the communication by ID,
 * merges workflow subject data into the variable resolution context, and returns an
 * interpolated [RenderedCommunication]. Returns null when the communication is missing or
 * inactive so callers can fall back to the default notification behaviour.
 */
@ApplicationScoped
class CommunicationResolver @Inject constructor(
    private val repository: CommunicationRepository,
    private val interpolator: TemplateVariableInterpolator,
)
{
    private val logger = LoggerFactory.getLogger(CommunicationResolver::class.java)

    fun resolve(
        communicationId: String?,
        subjectData: Map<String, String>,
        context: VariableResolutionContext,
    ): RenderedCommunication?
    {
        if (communicationId == null) return null

        val id = runCatching { UUID.fromString(communicationId) }.getOrElse {
            logger.warn("Invalid communication ID in workflow spec: '{}'", communicationId)
            return null
        }

        val communication = repository.findById(id) ?: run {
            logger.warn("Communication {} not found; falling back to default notification", id)
            return null
        }

        if (!communication.isActive || communication.isDeleted)
        {
            logger.warn("Communication {} is inactive or deleted; falling back", id)
            return null
        }

        val enrichedContext = context.copy(overrides = context.overrides + subjectData)
        val hasSeqTokens = communication.subject.contains("{{SEQ:") || communication.body.contains("{{SEQ:")

        return if (hasSeqTokens)
        {
            val resolvedSubject = interpolator.interpolateWithSequences(communication.subject, enrichedContext).resolved
            val resolvedBody = interpolator.interpolateWithSequences(communication.body, enrichedContext).resolved
            RenderedCommunication(subject = resolvedSubject, body = resolvedBody)
        }
        else
        {
            val resolvedSubject = interpolator.interpolate(communication.subject, enrichedContext).resolved
            val resolvedBody = interpolator.interpolate(communication.body, enrichedContext).resolved
            RenderedCommunication(subject = resolvedSubject, body = resolvedBody)
        }
    }
}
