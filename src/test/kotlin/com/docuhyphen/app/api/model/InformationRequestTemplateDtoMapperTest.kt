package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestConditionHiddenDataPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestTemplateDtoMapperTest
{
    @Test
    fun `template projection names unsupported authoring controls with server reasons`()
    {
        val definition = InformationRequestTemplateDefinition().apply {
            scopeKind = InformationRequestTemplateScopeKind.PERSONAL
            namespace = "process"
            templateKey = "collection-pattern"
            displayName = "Collection pattern"
            status = InformationRequestTemplateStatus.DRAFT
            createdAt = Timestamp.from(Instant.parse("2026-09-02T00:00:00Z"))
            updatedAt = Timestamp.from(Instant.parse("2026-09-02T00:00:00Z"))
        }

        val dto = InformationRequestTemplateDtoMapper.toDto(
            definition = definition,
            draftVersion = null,
            latestPublishedVersion = null,
        )

        val control = dto.unsupportedPolicyControls.single()
        assertEquals("document-evidence-policy", control.controlKey)
        assertEquals("Document Evidence Policy", control.label)
        assertTrue(control.reason.isNotBlank())
    }

    @Test
    fun `configuration copies condition rules and hidden response data policy`()
    {
        val version = InformationRequestTemplateVersionDto(
            id = UUID.randomUUID(),
            templateDefinitionId = UUID.randomUUID(),
            versionNumber = 1,
            status = InformationRequestTemplateStatus.DRAFT,
            conditionRules = listOf(
                InformationRequestTemplateConditionRuleDto(
                    id = UUID.randomUUID(),
                    ruleKey = "when-recorded-note-applies",
                    expressionVersion = 1,
                    hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
                ),
            ),
            createdAt = Timestamp.from(Instant.parse("2026-09-08T00:00:00Z")),
        )

        val configuration = InformationRequestTemplateConfigurationMapper.toRequest(version)

        val rule = configuration.conditionRules.single()
        assertEquals("when-recorded-note-applies", rule.ruleKey)
        assertEquals(InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE, rule.hiddenDataPolicy)
    }
}
