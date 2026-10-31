package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class ForeignTemplateVersionFixture(
    ownerScope: InformationRequestTemplateScopeKind,
    val versionId: UUID = UUID.randomUUID(),
) {
    val entitlementGuard: InformationRequestTemplateEntitlementGuard = mock()

    val references: InformationRequestTemplateReferenceService

    init {
        val foreignVersionId = versionId
        val definition = InformationRequestTemplateDefinition().apply {
            scopeKind = ownerScope
            scopeOrgId = UUID.randomUUID().takeIf { ownerScope == InformationRequestTemplateScopeKind.ORGANIZATION }
            scopeUserId = UUID.randomUUID().takeIf { ownerScope == InformationRequestTemplateScopeKind.PERSONAL }
            namespace = "process"
            templateKey = "collection-pattern"
            displayName = "Collection pattern"
        }
        val retiredAt = Timestamp.from(Instant.now())
        val version = InformationRequestTemplateVersion().apply {
            id = foreignVersionId
            templateDefinitionId = definition.id
            versionNumber = 4
            status = InformationRequestTemplateStatus.RETIRED
            publishedAt = retiredAt
            this.retiredAt = retiredAt
        }
        val definitions = mock<InformationRequestTemplateDefinitionRepository>()
        whenever(definitions.findById(definition.id)).thenReturn(definition)
        val versions = mock<InformationRequestTemplateVersionRepository>()
        whenever(versions.findById(foreignVersionId)).thenReturn(version)
        references = InformationRequestTemplateReferenceService(definitions, versions, entitlementGuard)
    }
}
