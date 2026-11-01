package com.docuhyphen.app.api.service.informationrequest.template.model

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.informationrequest.template.*
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

internal data class TemplateAuthoringFixture(
    val service: InformationRequestTemplateAuthoringService,
    val definitionRepository: InformationRequestTemplateDefinitionRepository,
    val versionRepository: InformationRequestTemplateVersionRepository,
    val configurationWriter: InformationRequestTemplateConfigurationWriter,
    val projectionLoader: InformationRequestTemplateProjectionLoader,
    val entitlementGuard: InformationRequestTemplateEntitlementGuard,
    val schemaCompatibility: InformationRequestTemplateSchemaCompatibility,
    val auditRecorder: AuditRecorder,
    val receipts: InMemoryTemplateReceiptStore,
    val savedDefinitions: List<InformationRequestTemplateDefinition>,
) {
    fun capturedAudit(): AuditEventDraft {
        val draft = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(draft.capture())
        return draft.firstValue
    }

    fun capturedAuditOwner(): AuditOwnerScope = capturedAudit().owner
}
