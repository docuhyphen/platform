package com.docuhyphen.app.api.service.informationrequest.template.model

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateDto
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.command.RevisionETag
import com.docuhyphen.app.api.service.informationrequest.template.InMemoryTemplateReceiptStore
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateAuthoringService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplatePublicationService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionFreezer

internal data class TemplatePublicationFixture(
    val service: InformationRequestTemplatePublicationService,
    val authoringService: InformationRequestTemplateAuthoringService,
    val versionRepository: InformationRequestTemplateVersionRepository,
    val freezer: InformationRequestTemplateVersionFreezer,
    val receipts: InMemoryTemplateReceiptStore,
    val definition: InformationRequestTemplateDefinition,
    val version: InformationRequestTemplateVersion,
    val projected: InformationRequestTemplateDto,
) {
    fun current(): CommandPrecondition =
        CommandPrecondition.ExpectedRevision(RevisionETag.of(version.id, version.revision))
}
