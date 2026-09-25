package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.service.command.RevisionETag
import java.util.UUID

/**
 * Strong validators for request runtime rows. They are based only on persisted revision counters,
 * so serialization order and timestamps never decide whether a command is stale.
 */
object InformationRequestETag
{
    fun aggregateOf(request: InformationRequest): String =
        RevisionETag.of(request.id, request.aggregateRevision)

    fun partiesOf(request: InformationRequest): String =
        RevisionETag.of(request.id, request.partyRevision)

    fun responsesOf(request: InformationRequest): String =
        RevisionETag.of(request.id, request.responseRevision)

    fun partyOf(party: InformationRequestParty): String =
        RevisionETag.of(party.id, party.partyRevision)

    fun requirementOf(revision: InformationRequestRequirementRevision): String =
        RevisionETag.of(revision.informationRequestRequirementId, revision.optimisticVersion)

    fun evidenceOf(requirementId: UUID, artifacts: List<InformationRequestEvidenceArtifact>): String =
        RevisionETag.of(requirementId, artifacts.sumOf { it.artifactRevision })

    fun artifactOf(artifact: InformationRequestEvidenceArtifact): String =
        RevisionETag.of(artifact.id, artifact.artifactRevision)

    fun submissionOf(stageKey: String?, contentHash: String): String =
        "\"submission:${stageKey ?: WHOLE_REQUEST}:$contentHash\""

    private const val WHOLE_REQUEST = "whole"
}
