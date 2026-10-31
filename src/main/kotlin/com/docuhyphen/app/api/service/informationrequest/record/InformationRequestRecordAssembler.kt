package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.*
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.submission.*
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestExternalSourceRecordAssembler
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeQueryService
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeStateReader
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestItemCorrectionService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.*
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestRecordAssembler @Inject constructor(
    private val transitionRepository: InformationRequestTransitionRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val packageRepository: InformationRequestSubmissionPackageRepository,
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val evidenceRepository: InformationRequestSubmissionEvidenceRepository,
    private val attestationRepository: InformationRequestSubmissionAttestationRepository,
    private val withdrawalRepository: InformationRequestSubmissionWithdrawalRepository,
    private val reviewRepository: InformationRequestReviewRepository,
    private val decisionRepository: InformationRequestReviewDecisionRepository,
    private val findingRepository: InformationRequestReviewFindingRepository,
    private val correctionRepository: InformationRequestCorrectionRepository,
    private val correctionItemRepository: InformationRequestCorrectionItemRepository,
    private val factRepository: InformationRequestAcceptedFactRepository,
    private val revocationRepository: InformationRequestAcceptedFactRevocationRepository,
    private val businessDecisionRepository: InformationRequestBusinessDecisionRepository,
    private val clockRepository: InformationRequestClockRepository,
    private val clockEventRepository: InformationRequestClockEventRepository,
    private val noticeStates: InformationRequestNoticeStateReader,
    private val fieldValues: FieldValueRevisionQueryService,
    private val recertificationRepository: InformationRequestFactRecertificationRepository,
    private val recertificationEvidenceRepository: InformationRequestFactRecertificationEvidenceRepository,
    private val externalSources: InformationRequestExternalSourceRecordAssembler,
    private val itemCorrections: InformationRequestItemCorrectionService,
)
{
    fun assemble(request: InformationRequest, disclosesRequirement: (UUID) -> Boolean): JsonObject {
        val decisions = mutableMapOf<UUID, Boolean>()
        val discloses =
            { requirementId: UUID -> decisions.getOrPut(requirementId) { disclosesRequirement(requirementId) } }
        val packages =
            packageRepository.findForRequest(request.id).sortedWith(compareBy({ it.submittedAt }, { it.packageNumber }))
        val items = itemRepository.findForPackages(packages.map { it.id })
        return buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("request", requestOf(request))
            put("history", historyOf(request.id))
            put("parties", partiesOf(request.id))
            put("packages", packagesOf(packages, items, discloses))
            put("attestations", attestationsOf(request.id, discloses))
            put("withdrawals", withdrawalsOf(request.id))
            put("reviews", reviewsOf(request.id, discloses))
            put("corrections", correctionsOf(request.id))
            put("itemCorrections", itemCorrectionsOf(request.id, items, discloses))
            put("acceptedFacts", factsOf(request.id, discloses))
            put("recertifications", recertificationsOf(request.id, discloses))
            put("businessDecisions", businessDecisionsOf(request.id))
            put("externalSources", externalSources.assemble(request.id, discloses))
            put("clocks", clocksOf(request.id))
            put("notices", noticesOf(request.id))
        }
    }

    private fun requestOf(request: InformationRequest) = buildJsonObject {
        put("id", request.id.toString())
        put("exchangeId", request.exchangeId.toString())
        put("templateVersionId", request.templateVersionId.toString())
        put("ownerType", request.ownerType.name)
        put("ownerId", (request.ownerOrganizationId ?: request.ownerUserId)?.toString())
        put("state", request.state.name)
        put("gatesExchangeClosure", request.gatesExchangeClosure)
        put("createdByPrincipalKind", request.createdByPrincipalKind?.name)
        put("createdByPrincipalId", request.createdByPrincipalId?.toString())
        put("createdAt", instant(request.createdAt))
        put("issuedAt", instant(request.issuedAt))
        put("firstViewedAt", instant(request.firstViewedAt))
        put("startedAt", instant(request.startedAt))
        put("satisfiedAt", instant(request.satisfiedAt))
        put("satisfiedByPackageId", request.satisfiedByPackageId?.toString())
        put("closedAt", instant(request.closedAt))
        put("cancelledAt", instant(request.cancelledAt))
        put("supersededAt", instant(request.supersededAt))
        put("supersededByRequestId", request.supersededByRequestId?.toString())
        put("expiredAt", instant(request.expiredAt))
    }

    private fun historyOf(requestId: UUID): JsonArray = buildJsonArray {
        transitionRepository.findForRequest(requestId).forEach { transition ->
            add(
                buildJsonObject {
                    put("transitionId", transition.id.toString())
                    put("sequenceNumber", transition.sequenceNumber)
                    put("mutation", transition.mutation.name)
                    put("fromState", transition.fromState?.name)
                    put("toState", transition.toState.name)
                    put("actorKind", transition.actorKind.name)
                    put("actorId", transition.actorId.toString())
                    put("reasonCode", transition.reasonCode)
                    put("partyId", transition.partyId?.toString())
                    put("occurredAt", instant(transition.occurredAt))
                },
            )
        }
    }

    private fun partiesOf(requestId: UUID): JsonArray = buildJsonArray {
        partyRepository.findForRequest(requestId).sortedWith(compareBy({ it.assignedAt }, { it.id })).forEach { party ->
            add(
                buildJsonObject {
                    put("partyId", party.id.toString())
                    put("roleKey", party.roleKey.name)
                    put("principalKind", party.principalKind?.name)
                    put("principalId", party.principalId?.toString())
                    put("subjectIdentityRefId", party.subjectIdentityRefId?.toString())
                    put("active", party.active)
                    put("assignedByPrincipalKind", party.assignedByPrincipalKind?.name)
                    put("assignedByPrincipalId", party.assignedByPrincipalId?.toString())
                    put("assignedAt", instant(party.assignedAt))
                    put("revokedAt", instant(party.revokedAt))
                },
            )
        }
    }

    private fun packagesOf(
        packages: List<InformationRequestSubmissionPackage>,
        items: List<InformationRequestSubmissionItem>,
        discloses: (UUID) -> Boolean,
    ): JsonArray
    {
        val itemsByPackage = items.groupBy { it.packageId }
        val evidence = evidenceRepository.findForPackages(packages.map { it.id }).groupBy { it.itemId }
        return buildJsonArray {
            packages.forEach { submission ->
                add(packageOf(submission, itemsByPackage[submission.id].orEmpty(), evidence, discloses))
            }
        }
    }

    private fun packageOf(
        submission: InformationRequestSubmissionPackage,
        items: List<InformationRequestSubmissionItem>,
        evidence: Map<UUID, List<InformationRequestSubmissionEvidence>>,
        discloses: (UUID) -> Boolean,
    ) = buildJsonObject {
        put("packageId", submission.id.toString())
        put("packageNumber", submission.packageNumber)
        put("stageKey", submission.stageKey)
        put("templateVersionId", submission.templateVersionId.toString())
        put("previousPackageId", submission.previousPackageId?.toString())
        put("completesRequest", submission.completesRequest)
        put("reviewRequired", submission.reviewRequired)
        put("contentHashSha256", submission.contentHashSha256)
        put("manifestHashSha256", submission.manifestHashSha256)
        put("submittedByPrincipalKind", submission.submittedByPrincipalKind.name)
        put("submittedByPrincipalId", submission.submittedByPrincipalId.toString())
        put("submittedAt", instant(submission.submittedAt))
        put(
            "items",
            buildJsonArray {
                items.sortedWith(compareBy({ it.requirementKey }, { it.occurrencePath }, { it.id })).forEach { item ->
                    add(
                        if (discloses(item.informationRequestRequirementId)) itemOf(item, evidence[item.id].orEmpty())
                        else withheldItemOf(item),
                    )
                }
            },
        )
    }

    private fun itemOf(item: InformationRequestSubmissionItem, evidence: List<InformationRequestSubmissionEvidence>) =
        buildJsonObject {
            put("itemId", item.id.toString())
            put("requirementId", item.informationRequestRequirementId.toString())
            put("requirementKey", item.requirementKey)
            put("requirementType", item.requirementType.name)
            put("occurrencePath", item.occurrencePath)
            put("completenessState", item.completenessState.name)
            put("disposition", item.disposition.name)
            put("narrative", item.narrative)
            put("responseRevision", item.responseRevision)
            put("respondedByPrincipalKind", item.respondedByPrincipalKind?.name)
            put("respondedByPrincipalId", item.respondedByPrincipalId?.toString())
            put("itemHashSha256", item.itemHashSha256)
            val value = item.fieldValueRevisionId?.let(fieldValues::valueOf)
            put("fieldValueRevisionId", item.fieldValueRevisionId?.toString())
            put("valueType", value?.valueType?.name)
            put("valueCleared", value?.cleared)
            put("value", value?.value ?: JsonNull)
            put(
                "evidence",
                buildJsonArray {
                    evidence.sortedWith(compareBy({ it.evidenceArtifactId }, { it.evidenceVersionNumber }))
                        .forEach { stored ->
                            add(
                                buildJsonObject {
                                    put("evidenceArtifactId", stored.evidenceArtifactId.toString())
                                    put("evidenceVersionId", stored.evidenceVersionId.toString())
                                    put("evidenceVersionNumber", stored.evidenceVersionNumber)
                                    put("documentVersionId", stored.documentVersionId?.toString())
                                    put("contentHashAlgorithm", stored.contentHashAlgorithm)
                                    put("contentHash", stored.contentHash)
                                    put("contentLength", stored.contentLength)
                                    put("contentVerification", stored.contentVerification)
                                    put("conformance", stored.conformance)
                                },
                            )
                        }
                },
            )
        }

    private fun withheldItemOf(item: InformationRequestSubmissionItem) = buildJsonObject {
        put("itemId", item.id.toString())
        put("requirementId", item.informationRequestRequirementId.toString())
        put("redacted", true)
    }

    private fun attestationsOf(requestId: UUID, discloses: (UUID) -> Boolean): JsonArray = buildJsonArray {
        attestationRepository.findForRequest(requestId).sortedWith(compareBy({ it.attestedAt }, { it.id }))
            .forEach { attestation ->
                if (!discloses(attestation.attestationRequirementId)) {
                    add(
                        buildJsonObject {
                            put("attestationId", attestation.id.toString())
                            put("requirementId", attestation.attestationRequirementId.toString())
                            put("redacted", true)
                        },
                    )
                    return@forEach
                }
                add(
                    buildJsonObject {
                        put("attestationId", attestation.id.toString())
                        put("requirementId", attestation.attestationRequirementId.toString())
                        put("stageKey", attestation.stageKey)
                        put("partyId", attestation.partyId.toString())
                        put("partyRole", attestation.partyRole.name)
                        put("principalKind", attestation.principalKind.name)
                        put("principalId", attestation.principalId.toString())
                        put("delegatedAuthorityId", attestation.delegatedAuthorityId?.toString())
                        put("decision", attestation.decision.name)
                        put("authenticationStrength", attestation.authenticationStrength.name)
                        put("attestedContentHashSha256", attestation.attestedContentHashSha256)
                        put("statementHashSha256", attestation.statementHashSha256)
                        put("attestedAt", instant(attestation.attestedAt))
                    },
                )
            }
    }

    private fun withdrawalsOf(requestId: UUID): JsonArray = buildJsonArray {
        withdrawalRepository.findForRequest(requestId).sortedWith(compareBy({ it.withdrawnAt }, { it.id }))
            .forEach { withdrawal ->
                add(
                    buildJsonObject {
                        put("packageId", withdrawal.packageId.toString())
                        put("reasonCode", withdrawal.reasonCode)
                        put("withdrawnByPrincipalKind", withdrawal.withdrawnByPrincipalKind.name)
                        put("withdrawnByPrincipalId", withdrawal.withdrawnByPrincipalId.toString())
                        put("withdrawnAt", instant(withdrawal.withdrawnAt))
                    },
                )
            }
    }

    private fun reviewsOf(requestId: UUID, discloses: (UUID) -> Boolean): JsonArray
    {
        val reviews = reviewRepository.findForRequest(requestId).sortedBy { it.reviewNumber }
        val ids = reviews.map { it.id }
        val decisions = decisionRepository.findForReviews(ids).groupBy { it.reviewId }
        val findings = findingRepository.findForReviews(ids).groupBy { it.reviewId }
        return buildJsonArray {
            reviews.forEach { review ->
                add(
                    buildJsonObject {
                        put("reviewId", review.id.toString())
                        put("packageId", review.packageId.toString())
                        put("reviewNumber", review.reviewNumber)
                        put("kind", review.kind.name)
                        put("state", review.state.name)
                        put("openedByPrincipalKind", review.openedByPrincipalKind.name)
                        put("openedByPrincipalId", review.openedByPrincipalId.toString())
                        put("openedAt", instant(review.openedAt))
                        put("settledByPrincipalKind", review.settledByPrincipalKind?.name)
                        put("settledByPrincipalId", review.settledByPrincipalId?.toString())
                        put("settledAt", instant(review.settledAt))
                        put(
                            "decisions",
                            buildJsonArray {
                                decisions[review.id].orEmpty().sortedBy { it.sequenceNumber }.forEach { decision ->
                                    if (!discloses(decision.requirementId)) {
                                        add(
                                            buildJsonObject {
                                                put("submissionItemId", decision.submissionItemId.toString())
                                                put("stageKey", decision.stageKey)
                                                put("redacted", true)
                                            },
                                        )
                                        return@forEach
                                    }
                                    add(
                                        buildJsonObject {
                                            put("submissionItemId", decision.submissionItemId.toString())
                                            put("stageKey", decision.stageKey)
                                            put("kind", decision.kind.name)
                                            put("outcome", decision.outcome.name)
                                            put("decidedByPrincipalKind", decision.decidedByPrincipalKind.name)
                                            put("decidedByPrincipalId", decision.decidedByPrincipalId.toString())
                                            put("decidedAt", instant(decision.decidedAt))
                                        },
                                    )
                                }
                            },
                        )
                        put(
                            "findings",
                            buildJsonArray {
                                findings[review.id].orEmpty().sortedBy { it.sequenceNumber }.forEach { finding ->
                                    if (!discloses(finding.requirementId)) {
                                        add(
                                            buildJsonObject {
                                                put("findingId", finding.id.toString())
                                                put("submissionItemId", finding.submissionItemId.toString())
                                                put("redacted", true)
                                            },
                                        )
                                        return@forEach
                                    }
                                    add(
                                        buildJsonObject {
                                            put("findingId", finding.id.toString())
                                            put("submissionItemId", finding.submissionItemId.toString())
                                            put("reasonCode", finding.reasonCode)
                                            put("severity", finding.severity.name)
                                            put("correctionScope", finding.correctionScope.name)
                                            put("recordedByPrincipalKind", finding.recordedByPrincipalKind.name)
                                            put("recordedByPrincipalId", finding.recordedByPrincipalId.toString())
                                            put("recordedAt", instant(finding.recordedAt))
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    private fun correctionsOf(requestId: UUID): JsonArray
    {
        val corrections =
            correctionRepository.findForRequest(requestId).sortedWith(compareBy({ it.openedAt }, { it.id }))
        val items = correctionItemRepository.findForCorrections(corrections.map { it.id }).groupBy { it.correctionId }
        return buildJsonArray {
            corrections.forEach { correction ->
                add(
                    buildJsonObject {
                        put("correctionId", correction.id.toString())
                        put("reviewId", correction.reviewId.toString())
                        put("packageId", correction.packageId.toString())
                        put("state", correction.state.name)
                        put("openedAt", instant(correction.openedAt))
                        put("closedAt", instant(correction.closedAt))
                        put("resubmittedPackageId", correction.resubmittedPackageId?.toString())
                        put(
                            "items",
                            buildJsonArray {
                                items[correction.id].orEmpty().sortedBy { it.submissionItemId }.forEach { item ->
                                    add(JsonPrimitive(item.submissionItemId.toString()))
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    private fun itemCorrectionsOf(
        requestId: UUID,
        items: List<InformationRequestSubmissionItem>,
        discloses: (UUID) -> Boolean,
    ): JsonArray {
        val requirements = items.associate { it.id to it.informationRequestRequirementId }
        return buildJsonArray {
            itemCorrections.correctionsFor(requestId).sortedWith(compareBy({ it.recordedAt }, { it.id }))
                .forEach { correction ->
                    val requirementId = requirements[correction.submissionItemId]
                    add(
                        if (requirementId != null && discloses(requirementId)) itemCorrectionOf(
                            correction,
                            requirementId
                        )
                        else withheldItemCorrectionOf(correction, requirementId),
                    )
                }
        }
    }

    private fun itemCorrectionOf(correction: InformationRequestItemCorrection, requirementId: UUID) = buildJsonObject {
        put("correctionId", correction.id.toString())
        put("packageId", correction.packageId.toString())
        put("submissionItemId", correction.submissionItemId.toString())
        put("requirementId", requirementId.toString())
        put("privacyRequestId", correction.privacyRequestId?.toString())
        put("correctedValue", correction.correctedValueJson?.let(Json::parseToJsonElement) ?: JsonNull)
        put("correctedNarrative", correction.correctedNarrative)
        put("reasonCode", correction.reasonCode)
        put("recordedByPrincipalKind", correction.recordedByPrincipalKind.name)
        put("recordedByPrincipalId", correction.recordedByPrincipalId.toString())
        put("recordedAt", instant(correction.recordedAt))
    }

    private fun withheldItemCorrectionOf(correction: InformationRequestItemCorrection, requirementId: UUID?) =
        buildJsonObject {
            put("correctionId", correction.id.toString())
            put("submissionItemId", correction.submissionItemId.toString())
            put("requirementId", requirementId?.toString())
            put("redacted", true)
        }

    private fun factsOf(requestId: UUID, discloses: (UUID) -> Boolean): JsonArray
    {
        val facts = factRepository.findFromRequest(requestId).sortedWith(compareBy({ it.promotedAt }, { it.id }))
        val revocations = revocationRepository.findForFacts(facts.map { it.id }).groupBy { it.factId }
        return buildJsonArray {
            facts.forEach { fact ->
                if (!discloses(fact.sourceRequirementId)) {
                    add(buildJsonObject {
                        put("acceptedFactId", fact.id.toString())
                        put("requirementId", fact.sourceRequirementId.toString())
                        put("redacted", true)
                    })
                    return@forEach
                }
                add(
                    buildJsonObject {
                        put("acceptedFactId", fact.id.toString())
                        put("purposeKey", fact.purposeKey)
                        put("fieldDefinitionId", fact.fieldDefinitionId.toString())
                        put("sourcePackageId", fact.sourcePackageId.toString())
                        put("sourceSubmissionItemId", fact.sourceSubmissionItemId.toString())
                        put("confidence", fact.confidence.name)
                        put("promotedByPrincipalKind", fact.promotedByPrincipalKind.name)
                        put("promotedByPrincipalId", fact.promotedByPrincipalId.toString())
                        put("promotedAt", instant(fact.promotedAt))
                        put("revokedAt", instant(revocations[fact.id]?.minOfOrNull { it.revokedAt }))
                    },
                )
            }
        }
    }

    private fun recertificationsOf(requestId: UUID, discloses: (UUID) -> Boolean): JsonArray
    {
        val recertifications =
            recertificationRepository.findForRequest(requestId).sortedWith(compareBy({ it.assentedAt }, { it.id }))
        val evidence = recertificationEvidenceRepository.findForRecertifications(recertifications.map { it.id })
            .groupBy { it.recertificationId }
        return buildJsonArray {
            recertifications.forEach { recertification ->
                if (!discloses(recertification.informationRequestRequirementId)) {
                    add(buildJsonObject {
                        put("recertificationId", recertification.id.toString())
                        put("requirementId", recertification.informationRequestRequirementId.toString())
                        put("redacted", true)
                    })
                    return@forEach
                }
                add(
                    buildJsonObject {
                        put("recertificationId", recertification.id.toString())
                        put("requirementId", recertification.informationRequestRequirementId.toString())
                        put("responseId", recertification.responseId.toString())
                        put("responseRevision", recertification.responseRevision)
                        put("factId", recertification.factId.toString())
                        put("purposeKey", recertification.purposeKey)
                        put("policyBasisKey", recertification.policyBasisKey)
                        put("valueType", recertification.valueType.name)
                        put("sourceInformationRequestId", recertification.sourceInformationRequestId.toString())
                        put("sourcePackageId", recertification.sourcePackageId.toString())
                        put("sourceSubmissionItemId", recertification.sourceSubmissionItemId.toString())
                        put("sourceRequirementId", recertification.sourceRequirementId.toString())
                        put("sourceFieldValueRevisionId", recertification.sourceFieldValueRevisionId.toString())
                        put("sourceReviewId", recertification.sourceReviewId?.toString())
                        put("assentedByPrincipalKind", recertification.assentedByPrincipalKind.name)
                        put("assentedByPrincipalId", recertification.assentedByPrincipalId.toString())
                        put("assentedAt", instant(recertification.assentedAt))
                        put(
                            "evidenceVersionIds",
                            buildJsonArray {
                                evidence[recertification.id].orEmpty().map { it.evidenceVersionId.toString() }.sorted()
                                    .forEach { add(JsonPrimitive(it)) }
                            },
                        )
                    },
                )
            }
        }
    }

    private fun businessDecisionsOf(requestId: UUID): JsonArray = buildJsonArray {
        businessDecisionRepository.findForRequest(requestId).sortedBy { it.decisionRevision }.forEach { decision ->
            add(
                buildJsonObject {
                    put("businessDecisionId", decision.id.toString())
                    put("owningProcessKey", decision.owningProcessKey)
                    put("outcomeCode", decision.outcomeCode)
                    put("kind", decision.kind.name)
                    put("decisionRevision", decision.decisionRevision)
                    put("recordedByPrincipalKind", decision.recordedByPrincipalKind.name)
                    put("recordedByPrincipalId", decision.recordedByPrincipalId.toString())
                    put("decidedAt", instant(decision.decidedAt))
                },
            )
        }
    }

    private fun clocksOf(requestId: UUID): JsonArray
    {
        val clocks = clockRepository.findForRequest(requestId).sortedBy { it.clockKey }
        val events = clockEventRepository.findForClocks(clocks.map { it.id }).groupBy { it.clockId }
        return buildJsonArray {
            clocks.forEach { clock ->
                add(
                    buildJsonObject {
                        put("clockId", clock.id.toString())
                        put("clockKey", clock.clockKey)
                        put("policyVersionId", clock.policyVersionId.toString())
                        put("urgency", clock.urgency.name)
                        put("receivedAt", instant(clock.receivedAt))
                        put("state", clock.state.name)
                        put("dueAt", instant(clock.dueAt))
                        put("dueCycle", clock.dueCycle)
                        put("overdueAt", instant(clock.overdueAt))
                        put("stoppedAt", instant(clock.stoppedAt))
                        put(
                            "events",
                            buildJsonArray {
                                events[clock.id].orEmpty().sortedBy { it.eventNumber }.forEach { event ->
                                    add(
                                        buildJsonObject {
                                            put("eventNumber", event.eventNumber)
                                            put("eventKind", event.eventKind.name)
                                            put("dueCycle", event.dueCycle)
                                            put("reasonCode", event.reasonCode)
                                            put("dueAt", instant(event.dueAt))
                                            put("occurredAt", instant(event.occurredAt))
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    private fun noticesOf(requestId: UUID): JsonArray = buildJsonArray {
        noticeStates.views(requestId).forEach { view ->
            add(
                buildJsonObject {
                    put("noticeIntentId", view.intent.id.toString())
                    put("noticeKind", view.intent.noticeKind.name)
                    put("partyId", view.intent.partyId.toString())
                    put("deliveryState", view.deliveryState.name)
                    put(
                        "maskedEndpoint",
                        InformationRequestNoticeQueryService.maskedEndpoint(
                            view.notice?.recipientEndpoint?.takeUnless { view.recipientWithheld },
                        ),
                    )
                    put("renderedContentHash", view.notice?.renderedContentHash)
                    put("renderedAt", instant(view.notice?.renderedAt))
                    put(
                        "attempts",
                        buildJsonArray {
                            view.attempts.forEach { attempt ->
                                add(
                                    buildJsonObject {
                                        put("attemptNumber", attempt.attemptNumber)
                                        put("outcome", attempt.outcome.name)
                                        put("attemptedAt", instant(attempt.attemptedAt))
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    private fun instant(value: Timestamp?): String? = value?.toInstant()?.toString()

    companion object
    {
        const val SCHEMA_VERSION = 4
    }
}
