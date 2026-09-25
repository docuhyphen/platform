package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import java.io.File
import java.util.UUID

enum class InformationRequestEvidenceAction
{
    UPLOAD,
    REPLACE,
    WITHDRAW,
    REMOVE,
}

data class InformationRequestEvidenceFile(
    val file: File,
    val declaredFileName: String,
    val declaredMediaType: String?,
    val encryptionMode: DocumentEncryptionMode,
)
{
    init
    {
        require(declaredFileName.isNotBlank()) { "An evidence file states its file name" }
        require(declaredFileName.length <= NAME_LIMIT) { "An evidence file name is at most $NAME_LIMIT characters" }
        require(declaredFileName.none { it.isISOControl() || it == '/' || it == '\\' })
        {
            "An evidence file name names one file without a path or control characters"
        }
        require(declaredMediaType == null || declaredMediaType.isNotBlank()) { "A declared media type is not blank" }
        require(declaredMediaType == null || declaredMediaType.length <= NAME_LIMIT)
        {
            "A declared media type is at most $NAME_LIMIT characters"
        }
    }

    private companion object
    {
        const val NAME_LIMIT = 255
    }
}

data class UploadInformationRequestEvidenceCommand(
    val requestId: UUID,
    val requirementId: UUID,
    val access: RequestAccessContext,
    val surface: InformationRequestEvidenceSurface,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val file: InformationRequestEvidenceFile,
    val attributes: InformationRequestEvidenceAttributes,
)

data class ReplaceInformationRequestEvidenceCommand(
    val requestId: UUID,
    val requirementId: UUID,
    val artifactId: UUID,
    val access: RequestAccessContext,
    val surface: InformationRequestEvidenceSurface,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val file: InformationRequestEvidenceFile,
    val attributes: InformationRequestEvidenceAttributes,
)

data class ChangeInformationRequestEvidenceStateCommand(
    val requestId: UUID,
    val requirementId: UUID,
    val artifactId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val reason: String?,
)

data class InformationRequestEvidenceVersionView(
    val version: InformationRequestEvidenceVersion,
    val documentVersion: DocumentVersion?,
)

data class InformationRequestEvidenceArtifactView(
    val artifact: InformationRequestEvidenceArtifact,
    val versions: List<InformationRequestEvidenceVersionView>,
)

data class InformationRequestEvidenceCommandResult(
    val artifact: InformationRequestEvidenceArtifactView,
    val evidenceETag: String,
    val artifactETag: String,
)

data class InformationRequestEvidenceTransition(
    val requirementId: UUID,
    val artifactId: UUID,
    val versionNumber: Int?,
    val action: InformationRequestEvidenceAction,
)

data class LockedInformationRequest(
    val exchange: Exchange,
    val request: InformationRequest,
)

enum class InformationRequestEvidenceContentUse
{
    DOWNLOAD,
    PREVIEW,
}

data class InformationRequestEvidenceList(
    val requirementId: UUID,
    val evidenceETag: String,
    val artifacts: List<InformationRequestEvidenceArtifactView>,
    val evaluation: InformationRequestEvidenceRequirementEvaluation?,
)

data class InformationRequestEvidenceContent(
    val file: File,
    val fileName: String,
    val mediaType: String,
    val inline: Boolean,
)
