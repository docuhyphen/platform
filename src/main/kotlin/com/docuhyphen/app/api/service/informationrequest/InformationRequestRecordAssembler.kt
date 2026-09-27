package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactRevocationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestBusinessDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewFindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionAttestationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestSubmissionWithdrawalRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.sql.Timestamp
import java.util.UUID

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
)
{
    fun assemble(request: InformationRequest): JsonObject = buildJsonObject {
        put("schemaVersion", SCHEMA_VERSION)
        put("request", requestOf(request))
        put("history", historyOf(request.id))
        put("parties", partiesOf(request.id))
        put("packages", packagesOf(request.id))
        put("attestations", attestationsOf(request.id))
        put("withdrawals", withdrawalsOf(request.id))
        put("reviews", reviewsOf(request.id))
        put("corrections", correctionsOf(request.id))
        put("acceptedFacts", factsOf(request.id))
        put("businessDecisions", businessDecisionsOf(request.id))
        put("clocks", clocksOf(request.id))
        put("notices", noticesOf(request.id))
    }

    private fun requestOf(request: InformationRequest) = buildJsonObject {
        put("id", request.id.toString())
        put("exchangeId", request.exchangeId.toString())
        put("templateVersionId", request.templateVersionId.toString())
        put("ownerType", request.ownerType.name)
        put("ownerId", (request.ownerOrganizationId ?: request.ownerUserId)?.toString())
        put("state", request.state.name)
        put("gatesExchangeClosure", request.gatesExchangeClosure)
        put("createdByAppUserId", request.createdByAppUserId?.toString())
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
                    put("assignedAt", instant(party.assignedAt))
                    put("revokedAt", instant(party.revokedAt))
                },
            )
        }
    }

    private fun packagesOf(requestId: UUID): JsonArray
    {
        val packages = packageRepository.findForRequest(requestId).sortedWith(compareBy({ it.submittedAt }, { it.packageNumber }))
        val ids = packages.map { it.id }
        val items = itemRepository.findForPackages(ids).groupBy { it.packageId }
        val evidence = evidenceRepository.findForPackages(ids).groupBy { it.itemId }
        return buildJsonArray {
            packages.forEach { submission -> add(packageOf(submission, items[submission.id].orEmpty(), evidence)) }
        }
    }

    private fun packageOf(
        submission: InformationRequestSubmissionPackage,
        items: List<InformationRequestSubmissionItem>,
        evidence: Map<UUID, List<InformationRequestSubmissionEvidence>>,
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
                    add(itemOf(item, evidence[item.id].orEmpty()))
                }
            },
        )
    }

    private fun itemOf(item: InformationRequestSubmissionItem, evidence: List<InformationRequestSubmissionEvidence>) = buildJsonObject {
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
                evidence.sortedWith(compareBy({ it.evidenceArtifactId }, { it.evidenceVersionNumber })).forEach { stored ->
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

    private fun attestationsOf(requestId: UUID): JsonArray = buildJsonArray {
        attestationRepository.findForRequest(requestId).sortedWith(compareBy({ it.attestedAt }, { it.id })).forEach { attestation ->
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
        withdrawalRepository.findForRequest(requestId).sortedWith(compareBy({ it.withdrawnAt }, { it.id })).forEach { withdrawal ->
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

    private fun reviewsOf(requestId: UUID): JsonArray
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
        val corrections = correctionRepository.findForRequest(requestId).sortedWith(compareBy({ it.openedAt }, { it.id }))
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

    private fun factsOf(requestId: UUID): JsonArray
    {
        val facts = factRepository.findFromRequest(requestId).sortedWith(compareBy({ it.promotedAt }, { it.id }))
        val revocations = revocationRepository.findForFacts(facts.map { it.id }).groupBy { it.factId }
        return buildJsonArray {
            facts.forEach { fact ->
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
                    put("maskedEndpoint", InformationRequestNoticeQueryService.maskedEndpoint(view.notice?.recipientEndpoint))
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
        const val SCHEMA_VERSION = 1
    }
}
