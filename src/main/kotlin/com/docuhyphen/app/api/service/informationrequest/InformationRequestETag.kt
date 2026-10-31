package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.command.RevisionETag
import java.util.*

object InformationRequestETag
{
    fun aggregateOf(request: InformationRequest): String =
        familyOf(AGGREGATE, request.id, request.aggregateRevision)

    fun partiesOf(request: InformationRequest): String =
        familyOf(PARTIES, request.id, request.partyRevision)

    fun responsesOf(request: InformationRequest): String =
        familyOf(RESPONSES, request.id, request.responseRevision)

    fun partyOf(party: InformationRequestParty): String =
        RevisionETag.of(party.id, party.partyRevision)

    fun requirementOf(revision: InformationRequestRequirementRevision): String =
        RevisionETag.of(revision.informationRequestRequirementId, revision.optimisticVersion)

    fun evidenceOf(requirementId: UUID, artifacts: List<InformationRequestEvidenceArtifact>): String =
        RevisionETag.of(requirementId, artifacts.sumOf { it.artifactRevision })

    fun artifactOf(artifact: InformationRequestEvidenceArtifact): String =
        RevisionETag.of(artifact.id, artifact.artifactRevision)

    fun clockOf(clock: InformationRequestClock): String =
        RevisionETag.of(clock.id, clock.clockRevision)

    fun submissionOf(stageKey: String?, contentHash: String): String =
        "\"submission:${stageKey ?: WHOLE_REQUEST}:$contentHash\""

    private fun familyOf(family: String, requestId: UUID, revision: Long): String =
        "\"$family:$requestId:$revision\""

    private const val WHOLE_REQUEST = "whole"
    private const val AGGREGATE = "aggregate"
    private const val PARTIES = "parties"
    private const val RESPONSES = "responses"
}
