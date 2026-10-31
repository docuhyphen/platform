package com.docuhyphen.app.api.model.blueprint

import com.docuhyphen.app.api.model.entity.BlueprintDocumentDefault
import com.docuhyphen.app.api.model.entity.BlueprintFieldDefault
import com.docuhyphen.app.api.model.entity.BlueprintParticipantDefault
import com.docuhyphen.app.api.model.entity.DocumentLibraryEntry
import java.util.*

data class BlueprintDocumentInstantiationDefault(
    val default: BlueprintDocumentDefault,
    val libraryEntry: DocumentLibraryEntry?,
)

data class BlueprintInformationRequestInstantiationSnapshot(
    val blueprintDefinitionId: UUID,
    val templateVersionId: UUID,
    val participantDefaults: List<BlueprintParticipantDefault>,
    val documentDefaults: List<BlueprintDocumentInstantiationDefault>,
    val fieldDefaults: List<BlueprintFieldDefault>,
)
