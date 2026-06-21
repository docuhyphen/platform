package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.model.dto.formatSequenceValue
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.VariableScope
import com.docuhyphen.app.api.repository.SequenceDefinitionRepository
import com.docuhyphen.app.api.repository.VariableDefinitionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class VariableResolutionContext(
    val user: AppUser,
    val organization: Organization?,
    val timestamp: Instant,
    val overrides: Map<String, String> = emptyMap(),
)

data class InterpolationResult(
    val resolved: String,
    val unresolvedTokens: List<String> = emptyList(),
)

@ApplicationScoped
class TemplateVariableInterpolator @Inject constructor(
    private val sequenceRepository: SequenceDefinitionRepository,
    private val variableRepository: VariableDefinitionRepository,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(TemplateVariableInterpolator::class.java)
        private val TOKEN_REGEX = Regex("""\{\{([^}]+)}}""")
    }

    fun interpolate(template: String, context: VariableResolutionContext): InterpolationResult
    {
        val unresolved = mutableListOf<String>()
        val resolved = TOKEN_REGEX.replace(template) { match ->
            val token = match.groupValues[1].trim()
            resolveToken(token, context, unresolved) ?: match.value
        }
        return InterpolationResult(resolved, unresolved)
    }

    @Transactional
    fun interpolateWithSequences(template: String, context: VariableResolutionContext): InterpolationResult
    {
        val unresolved = mutableListOf<String>()
        val resolved = TOKEN_REGEX.replace(template) { match ->
            val token = match.groupValues[1].trim()
            if (token.startsWith("SEQ:"))
            {
                resolveSequenceToken(token, context, unresolved)
            }
            else
            {
                resolveToken(token, context, unresolved) ?: match.value
            }
        }
        return InterpolationResult(resolved, unresolved)
    }

    private fun resolveToken(
        token: String,
        context: VariableResolutionContext,
        unresolved: MutableList<String>,
    ): String?
    {
        if (token.startsWith("SEQ:")) return null  // handled separately when transactional

        val systemResolved = resolveSystemToken(token, context)
        if (systemResolved != null) return systemResolved

        // Check caller-supplied overrides first
        val overrideValue = context.overrides[token]
        if (overrideValue != null) return overrideValue

        // Org variable
        if (context.organization != null)
        {
            val orgVar = variableRepository.findByOrganizationIdAndKeyAndIsDeletedFalse(
                context.organization.id, token
            )
            if (orgVar != null && orgVar.isActive) return orgVar.defaultValue ?: ""
        }

        // Personal variable
        val personalVar = variableRepository.findByCreatedByAppUserIdAndKeyAndIsDeletedFalse(
            context.user.id, token
        )
        if (personalVar != null && personalVar.isActive) return personalVar.defaultValue ?: ""

        unresolved.add(token)
        return null
    }

    private fun resolveSequenceToken(
        token: String,
        context: VariableResolutionContext,
        unresolved: MutableList<String>,
    ): String
    {
        val seqKey = token.removePrefix("SEQ:").trim()
        val orgId = context.organization?.id

        if (orgId == null)
        {
            unresolved.add(token)
            return "{{$token}}"
        }

        return try
        {
            @Suppress("UNCHECKED_CAST")
            val rows = entityManager.createNativeQuery(
                """UPDATE sequence_definition
                   SET current_value = current_value + 1
                   WHERE organization_id = :orgId AND key = :key AND is_active = TRUE AND is_deleted = FALSE
                   RETURNING current_value, pad_width, prefix, suffix"""
            )
                .setParameter("orgId", orgId)
                .setParameter("key", seqKey)
                .resultList

            if (rows.isEmpty())
            {
                logger.warn("Sequence key '{}' not found for org {}", seqKey, orgId)
                unresolved.add(token)
                "{{$token}}"
            }
            else
            {
                val row = rows[0] as Array<*>
                val value = (row[0] as Number).toLong()
                val padWidth = (row[1] as Number).toInt()
                val prefix = row[2] as String?
                val suffix = row[3] as String?
                formatSequenceValue(value, padWidth, prefix, suffix)
            }
        }
        catch (e: Exception)
        {
            logger.error("Failed to increment sequence '{}' for org {}", seqKey, orgId, e)
            unresolved.add(token)
            "{{$token}}"
        }
    }

    private fun resolveSystemToken(token: String, context: VariableResolutionContext): String?
    {
        val dt = context.timestamp.atZone(ZoneOffset.UTC)
        return when (token)
        {
            "USER_FIRST_NAME" -> context.user.person?.firstName ?: ""
            "USER_LAST_NAME"  -> context.user.person?.lastName ?: ""
            "USER_FULL_NAME"  ->
            {
                val first = context.user.person?.firstName ?: ""
                val last = context.user.person?.lastName ?: ""
                "$first $last".trim()
            }
            "USER_EMAIL"          -> context.user.email
            "ORG_NAME"            -> context.organization?.name ?: ""
            "ORG_REG_NUMBER"      -> context.organization?.registrationNumber ?: ""
            "CURRENT_DATE"        -> DateTimeFormatter.ofPattern("yyyy-MM-dd").format(dt)
            "CURRENT_YEAR"        -> dt.year.toString()
            "CURRENT_MONTH"       -> DateTimeFormatter.ofPattern("MMMM").format(dt)
            "CURRENT_MONTH_NUMBER" -> DateTimeFormatter.ofPattern("MM").format(dt)
            else -> null
        }
    }
}
