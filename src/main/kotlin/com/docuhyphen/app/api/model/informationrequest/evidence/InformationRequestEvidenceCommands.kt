package com.docuhyphen.app.api.model.informationrequest.evidence

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.document.DocumentVersionContent
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.io.File
import java.io.OutputStream
import java.util.*

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
        require(declaredFileName.none { it.isISOControl() || it.category == CharCategory.FORMAT || it == '/' || it == '\\' })
        {
            "An evidence file name names one file without a path, control characters or invisible formatting characters"
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
    val changeableByCaller: Boolean = false,
)

data class InformationRequestEvidenceVersionRecord(
    val evidenceVersionId: UUID,
    val evidenceArtifactId: UUID,
    val versionNumber: Int,
    val declaredFileName: String?,
    val createdBy: PrincipalRef,
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

class InformationRequestEvidenceContent(
    private val content: DocumentVersionContent,
    val fileName: String,
    val mediaType: String,
    val inline: Boolean,
) : AutoCloseable {
    val file: File get() = content.file

    fun transferTo(output: OutputStream) = content.transferTo(output)

    override fun close() = content.close()
}
